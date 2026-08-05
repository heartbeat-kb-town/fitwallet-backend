package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.payment.dto.*;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final int PIN_AUTH_TTL_SECONDS = 180;
    private static final int QR_SESSION_TTL_SECONDS = 180;
    private static final int MOCK_SCAN_DELAY_SECONDS = 3;
    private static final Long MOCK_STORE_ID = 1L;
    private static final BigDecimal MOCK_AMOUNT = BigDecimal.valueOf(4500);
    private static final int MOCK_PROCESS_DELAY_SECONDS = 2;
    private static final double MOCK_SUCCESS_RATE = 0.9;

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

        paymentMapper.markPinAuthUsed(userId);

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

    private PaymentResultResponse completeAndBuildResponse(Long userId, String paymentId, PaymentResultSessionInfo session){

    }
}
