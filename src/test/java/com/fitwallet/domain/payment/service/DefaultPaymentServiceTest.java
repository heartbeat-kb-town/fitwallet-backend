package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.benefit.dto.CardBenefitStatus;
import com.fitwallet.domain.benefit.dto.ValueType;
import com.fitwallet.domain.benefit.dto.response.BenefitDetailResponse;
import com.fitwallet.domain.benefit.dto.response.CardBenefitResponse;
import com.fitwallet.domain.payment.dto.*;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.exception.PaymentErrorCode;
import com.fitwallet.domain.payment.mapper.PaymentMapper;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.request.StoreQrScanRequest;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;
import com.fitwallet.domain.payment.dto.response.QrStatusResponse;
import com.fitwallet.domain.payment.dto.response.StoreQrScanResponse;
import com.fitwallet.domain.benefit.service.BenefitService;
import com.fitwallet.domain.payment.dto.response.PaymentResultResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DefaultPaymentServiceTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BenefitService benefitService;

    @InjectMocks
    private DefaultPaymentService paymentService;

    // 결제 비밀번호 확인
    @Test
    void PIN이_일치하면_인증_토큰을_발급한다() {
        given(paymentMapper.findUserPinInfo(1L)).willReturn(
                UserPinInfo.builder().paymentPinHash("encodedHash").pinFailCount(0).build());
        given(passwordEncoder.matches("123456", "encodedHash")).willReturn(true);

        PinVerifyResponse response = paymentService.verifyPin(1L, verifyRequest("123456"));

        assertThat(response.getPinAuthId()).startsWith("auth_");
        assertThat(response.getExpiresIn()).isEqualTo(180);
    }

    @Test
    void PIN이_일치하면_issuePinAuth만_호출되고_incrementPinFailCount는_호출되지_않는다() {
        given(paymentMapper.findUserPinInfo(1L)).willReturn(
                UserPinInfo.builder().paymentPinHash("encodedHash").pinFailCount(0).build());
        given(passwordEncoder.matches("123456", "encodedHash")).willReturn(true);

        paymentService.verifyPin(1L, verifyRequest("123456"));

        then(paymentMapper).should().issuePinAuth(eq(1L), anyString(), any());
        then(paymentMapper).should(never()).incrementPinFailCount(any());
    }

    @Test
    void PIN이_불일치하면_PIN_MISMATCH_예외를_던진다() {
        given(paymentMapper.findUserPinInfo(1L)).willReturn(
                UserPinInfo.builder().paymentPinHash("encodedHash").pinFailCount(0).build());
        given(passwordEncoder.matches("000000", "encodedHash")).willReturn(false);

        assertThatThrownBy(() -> paymentService.verifyPin(1L, verifyRequest("000000")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_MISMATCH);
    }

    @Test
    void PIN이_불일치하면_실패횟수를_증가시키고_인증토큰은_발급하지_않는다() {
        given(paymentMapper.findUserPinInfo(1L)).willReturn(
                UserPinInfo.builder().paymentPinHash("encodedHash").pinFailCount(0).build());
        given(passwordEncoder.matches("000000", "encodedHash")).willReturn(false);

        assertThatThrownBy(() -> paymentService.verifyPin(1L, verifyRequest("000000")))
                .isInstanceOf(BusinessException.class);

        then(paymentMapper).should().incrementPinFailCount(1L);
        then(paymentMapper).should(never()).issuePinAuth(any(), any(), any());
    }

    @Test
    void PIN을_설정하지_않은_유저는_비교_없이_불일치로_처리한다() {
        given(paymentMapper.findUserPinInfo(1L)).willReturn(
                UserPinInfo.builder().paymentPinHash(null).pinFailCount(0).build());

        assertThatThrownBy(() -> paymentService.verifyPin(1L, verifyRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_MISMATCH);

        then(passwordEncoder).should(never()).matches(any(), any());
    }

    // QR 생성 (CPM)

    @Test
    void 카드소유자이고_유효한_인증이면_QR을_생성한다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());

        QrGenerateResponse response = paymentService.generateQr(1L, qrRequest(1L, "auth_abc123"));

        assertThat(response.getQrToken()).startsWith("qrt_");
        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.PENDING);
        assertThat(response.getExpiresIn()).isEqualTo(180);
    }

    @Test
    void QR_생성에_성공하면_인증을_소비하고_세션을_저장한다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());

        paymentService.generateQr(1L, qrRequest(1L, "auth_abc123"));

        then(paymentMapper).should().markPinAuthUsed(1L);
        then(paymentMapper).should().insertPaymentSession(eq(1L), anyString(), eq(PaymentSessionStatus.PENDING), any());
    }

    @Test
    void 본인_소유_카드가_아니면_CARD_NOT_FOUND_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_abc123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_NOT_FOUND);

        then(paymentMapper).should(never()).findPinAuthInfo(any());
    }

    @Test
    void 인증_발급_이력이_없으면_PIN_AUTH_ID_INVALID_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder().pinAuthId(null).authIsUsed(false).build());

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_abc123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_AUTH_ID_INVALID);
    }

    @Test
    void pinAuthId가_일치하지_않으면_PIN_AUTH_ID_INVALID_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_real")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_AUTH_ID_INVALID);
    }

    @Test
    void 이미_사용된_인증이면_PIN_AUTH_ID_INVALID_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(true)
                        .build());

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_abc123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_AUTH_ID_INVALID);
    }

    @Test
    void 만료된_인증이면_PIN_AUTH_ID_INVALID_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().minusSeconds(1))
                        .authIsUsed(false)
                        .build());

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_abc123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_AUTH_ID_INVALID);
    }

    @Test
    void 인증이_유효하지_않으면_소비하거나_세션을_생성하지_않는다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder().pinAuthId(null).authIsUsed(false).build());

        assertThatThrownBy(() -> paymentService.generateQr(1L, qrRequest(1L, "auth_abc123")))
                .isInstanceOf(BusinessException.class);

        then(paymentMapper).should(never()).markPinAuthUsed(any());
        then(paymentMapper).should(never()).insertPaymentSession(any(), any(), any(), any());
    }

    // QR 상태 조회
    @Test
    void 존재하지_않는_토큰이면_QR_NOT_FOUND_예외를_던진다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_unknown")).willReturn(null);

        assertThatThrownBy(() -> paymentService.getQrStatus(1L, "qrt_unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.QR_NOT_FOUND);
    }

    @Test
    void PENDING_상태에서_만료시각이_지났으면_QR_EXPIRED_예외를_던지고_상태를_만료처리한다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_abc")).willReturn(
                QrSessionInfo.builder()
                        .status(PaymentSessionStatus.PENDING)
                        .expiresAt(LocalDateTime.now().minusSeconds(1))
                        .createdAt(LocalDateTime.now().minusSeconds(200))
                        .build());

        assertThatThrownBy(() -> paymentService.getQrStatus(1L, "qrt_abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.QR_EXPIRED);

        then(paymentMapper).should().markSessionExpired("qrt_abc");
    }

    @Test
    void 이미_EXPIRED_상태면_다시_만료처리하지_않고_예외만_던진다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_abc")).willReturn(
                QrSessionInfo.builder()
                        .status(PaymentSessionStatus.EXPIRED)
                        .expiresAt(LocalDateTime.now().minusSeconds(100))
                        .createdAt(LocalDateTime.now().minusSeconds(300))
                        .build());

        assertThatThrownBy(() -> paymentService.getQrStatus(1L, "qrt_abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.QR_EXPIRED);

        then(paymentMapper).should(never()).markSessionExpired(any());
    }

    @Test
    void 스캔_지연시간이_지나지_않았으면_PENDING_상태를_그대로_반환한다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_abc")).willReturn(
                QrSessionInfo.builder()
                        .status(PaymentSessionStatus.PENDING)
                        .expiresAt(LocalDateTime.now().plusSeconds(170))
                        .createdAt(LocalDateTime.now())
                        .build());

        QrStatusResponse response = paymentService.getQrStatus(1L, "qrt_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.PENDING);
        assertThat(response.getPaymentId()).isNull();
        then(paymentMapper).should(never()).markSessionScanned(any(), any());
    }

    @Test
    void 스캔_지연시간이_지나면_SCANNED로_전환하고_paymentId를_발급한다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_abc")).willReturn(
                QrSessionInfo.builder()
                        .status(PaymentSessionStatus.PENDING)
                        .expiresAt(LocalDateTime.now().plusSeconds(170))
                        .createdAt(LocalDateTime.now().minusSeconds(10))
                        .build());

        QrStatusResponse response = paymentService.getQrStatus(1L, "qrt_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.SCANNED);
        assertThat(response.getPaymentId()).startsWith("pay_");
        then(paymentMapper).should().markSessionScanned(eq("qrt_abc"), anyString());
    }

    @Test
    void 이미_SCANNED된_세션이면_재전환없이_기존_상태와_paymentId를_반환한다() {
        given(paymentMapper.findQrSessionByToken(1L, "qrt_abc")).willReturn(
                QrSessionInfo.builder()
                        .status(PaymentSessionStatus.SCANNED)
                        .paymentId("pay_existing123")
                        .expiresAt(LocalDateTime.now().plusSeconds(170))
                        .createdAt(LocalDateTime.now().minusSeconds(10))
                        .build());

        QrStatusResponse response = paymentService.getQrStatus(1L, "qrt_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.SCANNED);
        assertThat(response.getPaymentId()).isEqualTo("pay_existing123");
        then(paymentMapper).should(never()).markSessionScanned(any(), any());
    }

    private PinVerifyRequest verifyRequest(String pin) {
        PinVerifyRequest request = new PinVerifyRequest();
        ReflectionTestUtils.setField(request, "userCardId", 1L);
        ReflectionTestUtils.setField(request, "paymentPin", pin);
        return request;
    }

    private QrGenerateRequest qrRequest(Long userCardId, String pinAuthId) {
        QrGenerateRequest request = new QrGenerateRequest();
        ReflectionTestUtils.setField(request, "userCardId", userCardId);
        ReflectionTestUtils.setField(request, "pinAuthId", pinAuthId);
        return request;
    }

    private StoreQrScanRequest storeQrScanRequest(String storeQrToken, String pinAuthId, Long userCardId, BigDecimal amount) {
        StoreQrScanRequest request = new StoreQrScanRequest();
        ReflectionTestUtils.setField(request, "storeQrToken", storeQrToken);
        ReflectionTestUtils.setField(request, "pinAuthId", pinAuthId);
        ReflectionTestUtils.setField(request, "userCardId", userCardId);
        ReflectionTestUtils.setField(request, "amount", amount);
        return request;
    }

    //스캔 -> 결제 처리 및 결제 조회

    @Test
    void 존재하지_않는_paymentId면_PAYMENT_NOT_FOUND_예외를_던진다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_unknown")).willReturn(null);

        assertThatThrownBy(() -> paymentService.getPaymentResult(1L, "pay_unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void SCANNED_상태면_즉시_PROCESSING으로_전환하고_응답한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .status(PaymentSessionStatus.SCANNED)
                        .build());

        PaymentResultResponse response = paymentService.getPaymentResult(1L, "pay_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.PROCESSING);
        assertThat(response.getPaymentId()).isEqualTo("pay_abc");
        then(paymentMapper).should().markSessionProcessing(eq("pay_abc"), eq(20L), eq(BigDecimal.valueOf(4500)));
    }

    @Test
    void PROCESSING_상태이고_지연시간이_지나지_않았으면_PROCESSING을_그대로_반환한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .status(PaymentSessionStatus.PROCESSING)
                        .updatedAt(LocalDateTime.now())
                        .build());

        PaymentResultResponse response = paymentService.getPaymentResult(1L, "pay_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.PROCESSING);
        then(paymentMapper).should(never()).markSessionFailed(any());
        then(paymentMapper).should(never()).markSessionCompleted(any());
        then(paymentMapper).should(never())
                .insertPaymentTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 이미_FAILED된_세션이면_재처리없이_저장된_실패사유를_반환한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .status(PaymentSessionStatus.FAILED)
                        .failReason("MOCK_RANDOM_DECLINE")
                        .build());

        PaymentResultResponse response = paymentService.getPaymentResult(1L, "pay_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.FAILED);
        assertThat(response.getFailReason()).isEqualTo("MOCK_RANDOM_DECLINE");
        then(paymentMapper).should(never()).markSessionFailed(any());
    }

    @Test
    void 이미_COMPLETED된_세션이면_저장된_결과를_그대로_조회해서_반환한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .paymentSessionId(10L)
                        .status(PaymentSessionStatus.COMPLETED)
                        .build());
        given(paymentMapper.findPaymentResultBySessionId(10L)).willReturn(
                PaymentResultResponse.builder()
                        .paymentId("pay_abc")
                        .status(PaymentSessionStatus.COMPLETED)
                        .storeName("스타벅스 강남점")
                        .build());

        PaymentResultResponse response = paymentService.getPaymentResult(1L, "pay_abc");

        assertThat(response.getStatus()).isEqualTo(PaymentSessionStatus.COMPLETED);
        assertThat(response.getStoreName()).isEqualTo("스타벅스 강남점");
        then(paymentMapper).should(never()).markSessionCompleted(any());
        then(paymentMapper).should(never())
                .insertPaymentTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    //놓친혜택
    @Test
    void 실제_받은_혜택보다_더_유리한_카드가_있으면_놓친혜택을_계산한다() {
        CardBenefitResponse usedCard = CardBenefitResponse.builder()
                .userCardId(1L)
                .status(CardBenefitStatus.CONDITION_NOT_MET)
                .build();
        CardBenefitResponse betterCard = CardBenefitResponse.builder()
                .userCardId(5L)
                .status(CardBenefitStatus.AVAILABLE)
                .benefit(BenefitDetailResponse.builder().benefitServiceId(125L).build())
                .build();

        given(paymentMapper.findBenefitAmountInfo(125L)).willReturn(
                BenefitAmountInfo.builder()
                        .valueType(ValueType.RATE)
                        .valueNumber(BigDecimal.valueOf(20))
                        .perTxLimitAmount(BigDecimal.valueOf(4000))
                        .build());

        MissedBenefitInfo result = paymentService.calculateMissedBenefit(
                List.of(usedCard, betterCard), 1L, BigDecimal.valueOf(4500), BigDecimal.ZERO);

        assertThat(result.getBetterUserCardId()).isEqualTo(5L);
        assertThat(result.getAlternativeDiscountAmount()).isEqualByComparingTo("900");
        assertThat(result.getMissedAmount()).isEqualByComparingTo("900");
    }

    @Test
    void 더_유리한_카드가_없으면_놓친혜택은_전부_null이다() {
        CardBenefitResponse usedCard = CardBenefitResponse.builder()
                .userCardId(1L)
                .status(CardBenefitStatus.AVAILABLE)
                .build();
        CardBenefitResponse worseCard = CardBenefitResponse.builder()
                .userCardId(2L)
                .status(CardBenefitStatus.NO_BENEFIT)
                .build();

        MissedBenefitInfo result = paymentService.calculateMissedBenefit(
                List.of(usedCard, worseCard), 1L, BigDecimal.valueOf(4500), BigDecimal.valueOf(500));

        assertThat(result.getBetterUserCardId()).isNull();
        assertThat(result.getAlternativeDiscountAmount()).isNull();
        assertThat(result.getMissedAmount()).isNull();
    }

    // 가맹점 QR 스캔 (MPM)

    @Test
    void 유효한_QR과_인증이면_가맹점_정보를_응답한다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());
        given(paymentMapper.findStoreByQrToken("FITWALLET-QR-00020")).willReturn(
                StoreInfo.builder().storeId(20L).storeName("스타벅스 세종대점").build());

        StoreQrScanResponse response = paymentService.scanStoreQr(1L,
                storeQrScanRequest("FITWALLET-QR-00020", "auth_abc123", 1L, BigDecimal.valueOf(8000)));

        assertThat(response.getPaymentId()).startsWith("pay_");
        assertThat(response.getStoreId()).isEqualTo(20L);
        assertThat(response.getStoreName()).isEqualTo("스타벅스 세종대점");
        assertThat(response.getAmount()).isEqualByComparingTo("8000");
    }

    @Test
    void 스캔에_성공하면_인증을_소비하고_세션을_저장한다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());
        given(paymentMapper.findStoreByQrToken("FITWALLET-QR-00020")).willReturn(
                StoreInfo.builder().storeId(20L).storeName("스타벅스 세종대점").build());

        paymentService.scanStoreQr(1L,
                storeQrScanRequest("FITWALLET-QR-00020", "auth_abc123", 1L, BigDecimal.valueOf(8000)));

        then(paymentMapper).should().markPinAuthUsed(1L);
        then(paymentMapper).should().insertScannedPaymentSession(
                eq(1L), anyString(), anyString(), eq(20L), eq(BigDecimal.valueOf(8000)), any());
    }

    @Test
    void QR_토큰_형식이_올바르지_않으면_QR_TOKEN_INVALID_예외를_던진다() {
        assertThatThrownBy(() -> paymentService.scanStoreQr(1L,
                storeQrScanRequest("이상한-QR-값", "auth_abc123", 1L, BigDecimal.valueOf(8000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.QR_TOKEN_INVALID);

        then(paymentMapper).should(never()).existsUserCard(any(), any());
    }

    @Test
    void 본인_소유_카드가_아니면_CARD_NOT_FOUND_예외를_던진다_스캔() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> paymentService.scanStoreQr(1L,
                storeQrScanRequest("FITWALLET-QR-00020", "auth_abc123", 1L, BigDecimal.valueOf(8000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CardErrorCode.CARD_NOT_FOUND);

        then(paymentMapper).should(never()).findPinAuthInfo(any());
    }

    @Test
    void 인증_정보가_유효하지_않으면_PIN_AUTH_ID_INVALID_예외를_던진다_스캔() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder().pinAuthId(null).authIsUsed(false).build());

        assertThatThrownBy(() -> paymentService.scanStoreQr(1L,
                storeQrScanRequest("FITWALLET-QR-00020", "auth_abc123", 1L, BigDecimal.valueOf(8000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PIN_AUTH_ID_INVALID);

        then(paymentMapper).should(never()).findStoreByQrToken(any());
    }

    @Test
    void 등록되지_않은_가맹점이면_STORE_NOT_FOUND_예외를_던진다() {
        given(paymentMapper.existsUserCard(1L, 1L)).willReturn(true);
        given(paymentMapper.findPinAuthInfo(1L)).willReturn(
                PinAuthInfo.builder()
                        .pinAuthId("auth_abc123")
                        .authExpiresAt(LocalDateTime.now().plusSeconds(60))
                        .authIsUsed(false)
                        .build());
        given(paymentMapper.findStoreByQrToken("FITWALLET-QR-00099")).willReturn(null);

        assertThatThrownBy(() -> paymentService.scanStoreQr(1L,
                storeQrScanRequest("FITWALLET-QR-00099", "auth_abc123", 1L, BigDecimal.valueOf(8000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.STORE_NOT_FOUND);

        then(paymentMapper).should(never()).markPinAuthUsed(any());
        then(paymentMapper).should(never())
                .insertScannedPaymentSession(any(), any(), any(), any(), any(), any());
    }

    // 결제 승인 요청 (MPM)

    @Test
    void 존재하지_않는_paymentId로_승인_요청하면_PAYMENT_NOT_FOUND_예외를_던진다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_unknown")).willReturn(null);

        assertThatThrownBy(() -> paymentService.approvePayment(1L, "pay_unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 이미_COMPLETED된_결제를_다시_승인요청하면_기존_결과를_그대로_반환한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .paymentSessionId(10L)
                        .status(PaymentSessionStatus.COMPLETED)
                        .build());
        given(paymentMapper.findPaymentResultBySessionId(10L)).willReturn(
                PaymentResultResponse.builder()
                        .paymentId("pay_abc")
                        .status(PaymentSessionStatus.COMPLETED)
                        .storeName("스타벅스 세종대점")
                        .build());

        PaymentApproveResult result = paymentService.approvePayment(1L, "pay_abc");

        assertThat(result.isAlreadyProcessed()).isTrue();
        assertThat(result.getResponse().getStatus()).isEqualTo(PaymentSessionStatus.COMPLETED);
        assertThat(result.getResponse().getStoreName()).isEqualTo("스타벅스 세종대점");
        then(paymentMapper).should(never()).markSessionCompleted(any());
        then(paymentMapper).should(never())
                .insertPaymentTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 이미_FAILED된_결제를_다시_승인요청하면_저장된_실패사유를_그대로_반환한다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .status(PaymentSessionStatus.FAILED)
                        .failReason("MOCK_RANDOM_DECLINE")
                        .build());

        PaymentApproveResult result = paymentService.approvePayment(1L, "pay_abc");

        assertThat(result.isAlreadyProcessed()).isFalse();
        assertThat(result.getResponse().getStatus()).isEqualTo(PaymentSessionStatus.FAILED);
        assertThat(result.getResponse().getFailReason()).isEqualTo("MOCK_RANDOM_DECLINE");
        then(paymentMapper).should(never()).markSessionFailed(any());
    }

    @Test
    void 세션이_만료됐으면_PAYMENT_SESSION_EXPIRED_예외를_던진다() {
        given(paymentMapper.findSessionByPaymentId(1L, "pay_abc")).willReturn(
                PaymentResultSessionInfo.builder()
                        .status(PaymentSessionStatus.SCANNED)
                        .expiresAt(LocalDateTime.now().minusSeconds(1))
                        .build());

        assertThatThrownBy(() -> paymentService.approvePayment(1L, "pay_abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_SESSION_EXPIRED);

        then(paymentMapper).should(never()).markSessionFailed(any());
        then(paymentMapper).should(never()).markSessionCompleted(any());
    }
}