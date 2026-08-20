package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.benefit.dto.response.PaymentBenefitResponse;
import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.payment.dto.*;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.request.StoreQrScanRequest;
import com.fitwallet.domain.payment.dto.response.*;
import com.fitwallet.domain.payment.exception.PaymentErrorCode;
import com.fitwallet.domain.payment.mapper.PaymentMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final int PIN_AUTH_TTL_SECONDS = 180;
    private static final int QR_SESSION_TTL_SECONDS = 180;
    private static final int MOCK_SCAN_DELAY_SECONDS = 3;
    private static final Long MOCK_STORE_ID = 20L;
    private static final BigDecimal MOCK_AMOUNT = BigDecimal.valueOf(4500);
    private static final int MOCK_PROCESS_DELAY_SECONDS = 2;
    private static final double MOCK_SUCCESS_RATE = 0.9;
    private static final Pattern STORE_QR_TOKEN_PATTERN = Pattern.compile("^FITWALLET-QR-\\d{5}$");

    private final PaymentMapper paymentMapper;
    private final PasswordEncoder passwordEncoder;
    private final BenefitService benefitService;

    @Override
    @Transactional(noRollbackFor = BusinessException.class) //incrementPinFailCount 실행 위해
    public PinVerifyResponse verifyPin(Long userId, PinVerifyRequest request) {
        UserPinInfo userPinInfo = paymentMapper.findUserPinInfo(userId);

        boolean matched = userPinInfo.getPaymentPinHash() != null && passwordEncoder.matches(request.getPaymentPin(), userPinInfo.getPaymentPinHash());

        if(!matched){
            paymentMapper.incrementPinFailCount(userId);
            int remainingAttempts = Math.max(MAX_PIN_ATTEMPTS - (userPinInfo.getPinFailCount() + 1), 0);
            throw new BusinessException(PaymentErrorCode.PIN_MISMATCH, PinMismatchResponse.builder().remainingAttempts(remainingAttempts).build());
        }

        String pinAuthId = "auth_" + UUID.randomUUID().toString().replace("-","");
        paymentMapper.issuePinAuth(userId, pinAuthId, LocalDateTime.now().plusSeconds(PIN_AUTH_TTL_SECONDS));

        return PinVerifyResponse.builder().pinAuthId(pinAuthId).expiresIn(PIN_AUTH_TTL_SECONDS).build();
    }

    @Override
    @Transactional
    public QrGenerateResponse generateQr(Long userId, QrGenerateRequest request){
        if (!paymentMapper.existsUserCard(userId, request.getUserCardId())) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        PinAuthInfo pinAuthInfo = paymentMapper.findPinAuthInfo(userId);
        boolean valid = pinAuthInfo.getPinAuthId() != null
                && pinAuthInfo.getPinAuthId().equals(request.getPinAuthId())
                && !pinAuthInfo.isAuthIsUsed()
                && pinAuthInfo.getAuthExpiresAt().isAfter(LocalDateTime.now());

        if(!valid){ //유효하지 않은 경우
            throw new BusinessException(PaymentErrorCode.PIN_AUTH_ID_INVALID);
        }

        String qrToken = "qrt_" + UUID.randomUUID().toString().replace("-", "");

        paymentMapper.insertPaymentSession(request.getUserCardId(), qrToken, PaymentSessionStatus.PENDING, LocalDateTime.now().plusSeconds(QR_SESSION_TTL_SECONDS));

        return QrGenerateResponse.builder()
                .qrToken(qrToken)
                .status(PaymentSessionStatus.PENDING)
                .expiresIn(QR_SESSION_TTL_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public QrStatusResponse getQrStatus(Long userId, String qrToken){
        QrSessionInfo qrSession = paymentMapper.findQrSessionByToken(userId, qrToken);
        if(qrSession==null){
            throw new BusinessException(PaymentErrorCode.QR_NOT_FOUND);
        }

        boolean expired = qrSession.getStatus() == PaymentSessionStatus.EXPIRED
                || (qrSession.getStatus() == PaymentSessionStatus.PENDING && qrSession.getExpiresAt().isBefore(LocalDateTime.now()));

        if(expired){
            if(qrSession.getStatus()!=PaymentSessionStatus.EXPIRED){
                paymentMapper.markSessionExpired(qrToken);
            }
            throw new BusinessException(PaymentErrorCode.QR_EXPIRED);
        }

        //PENDING 상태에서 3초 지나면 SCANNED 로 전환
        boolean shouldAutoScan = qrSession.getStatus() == PaymentSessionStatus.PENDING
                && qrSession.getCreatedAt().plusSeconds(MOCK_SCAN_DELAY_SECONDS).isBefore(LocalDateTime.now());

        if(shouldAutoScan){
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "");
            paymentMapper.markSessionScanned(qrToken, paymentId);
            return QrStatusResponse.builder().status(PaymentSessionStatus.SCANNED).paymentId(paymentId).build();
        }

        return QrStatusResponse.builder().status(qrSession.getStatus()).paymentId(qrSession.getPaymentId()).build();
    }

    @Override
    @Transactional
    public PaymentResultResponse getPaymentResult(Long userId, String paymentId){
        PaymentResultSessionInfo session = paymentMapper.findSessionByPaymentId(userId, paymentId);
        if (session == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        if (session.getStatus() == PaymentSessionStatus.SCANNED){
            paymentMapper.markSessionProcessing(paymentId, MOCK_STORE_ID, MOCK_AMOUNT);
            return PaymentResultResponse.builder()
                    .paymentId(paymentId)
                    .status(PaymentSessionStatus.PROCESSING)
                    .build();
        }

        if(session.getStatus() == PaymentSessionStatus.PROCESSING){
            boolean delayPassed = session.getUpdatedAt().plusSeconds(MOCK_PROCESS_DELAY_SECONDS).isBefore(LocalDateTime.now());
            if(!delayPassed){ //아직 2초 지나지 않았으면
                return PaymentResultResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentSessionStatus.PROCESSING)
                        .build();
            }

            boolean approved = Math.random() < MOCK_SUCCESS_RATE; //90% 구간 해당하면 true
            if (!approved) { //승인 거절 10%
                paymentMapper.markSessionFailed(paymentId);
                return PaymentResultResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentSessionStatus.FAILED)
                        .failReason("MOCK_RANDOM_DECLINE")
                        .build();
            }

            return completeAndBuildResponse(userId, paymentId, session);
        }

        if(session.getStatus() == PaymentSessionStatus.FAILED){
            return PaymentResultResponse.builder()
                    .paymentId(paymentId)
                    .status(PaymentSessionStatus.FAILED)
                    .failReason(session.getFailReason())
                    .build();
        }

        //이미 COMPLETED로 끝난 세션을 다시 조회할 때 결과 리턴
        return paymentMapper.findPaymentResultBySessionId(session.getPaymentSessionId());
    }

    @Override
    @Transactional
    public StoreQrScanResponse scanStoreQr(Long userId, StoreQrScanRequest request){
        if (!STORE_QR_TOKEN_PATTERN.matcher(request.getStoreQrToken()).matches()) {
            throw new BusinessException(PaymentErrorCode.QR_TOKEN_INVALID);
        }

        if (!paymentMapper.existsUserCard(userId, request.getUserCardId())) {
            throw new BusinessException(CardErrorCode.CARD_NOT_FOUND);
        }

        PinAuthInfo pinAuthInfo = paymentMapper.findPinAuthInfo(userId);
        boolean pinAuthValid = pinAuthInfo.getPinAuthId() != null
                && pinAuthInfo.getPinAuthId().equals(request.getPinAuthId())
                && !pinAuthInfo.isAuthIsUsed()
                && pinAuthInfo.getAuthExpiresAt().isAfter(LocalDateTime.now());

        if (!pinAuthValid) {
            throw new BusinessException(PaymentErrorCode.PIN_AUTH_ID_INVALID);
        }

        StoreInfo storeInfo = paymentMapper.findStoreByQrToken(request.getStoreQrToken());
        if (storeInfo == null) {
            throw new BusinessException(PaymentErrorCode.STORE_NOT_FOUND);
        }

        String sessionToken = "mpm_" + UUID.randomUUID().toString().replace("-", "");
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "");

        paymentMapper.insertScannedPaymentSession(request.getUserCardId(), sessionToken, paymentId,
                storeInfo.getStoreId(), request.getAmount(), LocalDateTime.now().plusSeconds(QR_SESSION_TTL_SECONDS));

        return StoreQrScanResponse.builder()
                .paymentId(paymentId)
                .storeId(storeInfo.getStoreId())
                .storeName(storeInfo.getStoreName())
                .amount(request.getAmount())
                .build();
    }

    @Override
    @Transactional
    public PaymentApproveResult approvePayment(Long userId, String paymentId){
        PaymentResultSessionInfo session = paymentMapper.findSessionByPaymentId(userId, paymentId);
        if (session == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        if(session.getStatus() == PaymentSessionStatus.COMPLETED){
            PaymentResultResponse response = paymentMapper.findPaymentResultBySessionId(session.getPaymentSessionId());
            return  PaymentApproveResult.builder().response(response).alreadyProcessed(true).build();
        }

        if(session.getStatus() == PaymentSessionStatus.FAILED){
            PaymentResultResponse response = PaymentResultResponse.builder()
                    .paymentId(paymentId).status(PaymentSessionStatus.FAILED).failReason(session.getFailReason()).build();
            return PaymentApproveResult.builder().response(response).alreadyProcessed(false).build();
        }

        if(session.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new BusinessException(PaymentErrorCode.PAYMENT_SESSION_EXPIRED);
        }

        boolean approved = Math.random() < MOCK_SUCCESS_RATE;
        if(!approved){
            paymentMapper.markSessionFailed(paymentId);
            PaymentResultResponse response = PaymentResultResponse.builder()
                    .paymentId(paymentId).status(PaymentSessionStatus.FAILED).failReason("MOCK_RANDOM_DECLINE").build();
            return PaymentApproveResult.builder().response(response).alreadyProcessed(false).build();
        }

        PaymentResultResponse response = completeAndBuildResponse(userId, paymentId, session);
        return PaymentApproveResult.builder().response(response).alreadyProcessed(false).build();
    }


    /**
     * 혜택 산출은 하지 않고 <b>benefit 도메인이 판정한 결과를 읽기만 한다.</b> 예상 혜택 화면과
     * 결제 결과 화면이 같은 계산기를 타야 같은 금액을 답한다.
     * <p>
     * 저장 단위가 컬럼마다 다르다 — {@code discount_amount}만 네이티브(CASHBACK=원, ACCUMULATE=포인트)이고
     * {@code final_amount}·{@code alternative_discount_amount}·{@code missed_amount}는 원화다.
     * 놓친 혜택 두 컬럼이 원화인 것은 행에 {@code better_user_card_id}만 있고
     * {@code better_benefit_service_id}가 없어 읽는 쪽이 통화를 판별할 수 없기 때문이다(스키마 주석 참고).
     */
    PaymentResultResponse completeAndBuildResponse(Long userId, String paymentId, PaymentResultSessionInfo session) {
        Long userCardId = session.getUserCardId();
        Long storeId = session.getStoreId();
        BigDecimal amount = session.getAmount();

        List<PaymentBenefitResponse> benefits = benefitService.findPaymentBenefits(userId, storeId, amount);
        PaymentBenefitResponse applied = benefits.stream()
                .filter(benefit -> benefit.getUserCardId().equals(userCardId))
                .findFirst()
                .orElse(null);

        Long appliedBenefitServiceId = applied == null ? null : applied.getBenefitServiceId();
        // 이게 없으면 BenefitMapper.findUsage(WHERE applied_tier_id = ?)가 이 결제를 세지 못해
        // 앱 결제로는 한도 잔여가 영영 줄지 않는다.
        Long appliedTierId = applied == null ? null : applied.getTierId();
        // ⚠️ 네이티브다(CASHBACK=원, ACCUMULATE=포인트 개수). expectedAmount(원화)를 넣지 말 것 —
        // 지금은 krw_per_point가 전부 1.0000이라 바꿔 넣어도 숫자가 같아 아무 테스트도 깨지지 않는다.
        BigDecimal discountAmount = applied == null ? BigDecimal.ZERO : applied.getNativeAmount();
        BigDecimal receivedKrw = applied == null ? BigDecimal.ZERO : applied.getExpectedAmount();

        // 빼는 값은 네이티브가 아니라 원화다. discountAmount를 그대로 빼면 적립 건에서
        // 포인트 개수를 원화에서 빼게 된다 — 3,000P 적립에 1포인트 0.8원이면 2,400원을 빼야 한다.
        BigDecimal finalAmount = amount.subtract(receivedKrw);

        MissedBenefitInfo missedBenefit = calculateMissedBenefit(benefits, userCardId, receivedKrw);

        paymentMapper.insertPaymentTransaction(userCardId, storeId, session.getPaymentSessionId(),
                amount, discountAmount, finalAmount, LocalDateTime.now(), appliedBenefitServiceId, appliedTierId,
                missedBenefit.getBetterUserCardId(), missedBenefit.getAlternativeDiscountAmount(), missedBenefit.getMissedAmount());
        paymentMapper.updateDebitCardBalanceAfterPayment(
                userId, userCardId, session.getPaymentSessionId());
        paymentMapper.markSessionCompleted(paymentId);
        paymentMapper.markPinAuthUsed(userId);

        return paymentMapper.findPaymentResultBySessionId(session.getPaymentSessionId());
    }

    /**
     * 놓친 혜택(더 유리한 카드). {@code benefits}는 이미 원화 기대혜택액 내림차순이므로
     * <b>자기 카드를 뺀 첫 번째가 곧 최선 대안</b>이다 — 다시 순회해 최댓값을 구하지 않는다.
     * <p>
     * 비교는 원화 축에서 한다. 두 카드의 혜택 타입이 다르면 네이티브끼리는 단위가 섞인다.
     * <p>
     * package-private: {@code DefaultPaymentServiceTest}에서 {@code Math.random()} 분기 없이 직접 테스트하기 위함.
     *
     * @param receivedKrw 실제 쓴 카드로 받은 혜택(원화). 혜택이 없었으면 0
     */
    MissedBenefitInfo calculateMissedBenefit(List<PaymentBenefitResponse> benefits, Long usedUserCardId,
                                              BigDecimal receivedKrw) {
        PaymentBenefitResponse best = benefits.stream()
                .filter(benefit -> !benefit.getUserCardId().equals(usedUserCardId))
                .findFirst()
                .orElse(null);

        if (best == null || best.getExpectedAmount().compareTo(receivedKrw) <= 0) {
            return MissedBenefitInfo.builder().build();
        }

        return MissedBenefitInfo.builder()
                .betterUserCardId(best.getUserCardId())
                .alternativeDiscountAmount(best.getExpectedAmount())
                .missedAmount(best.getExpectedAmount().subtract(receivedKrw))
                .build();
    }
}
