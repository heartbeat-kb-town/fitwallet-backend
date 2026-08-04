package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.UserPinInfo;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.exception.PaymentErrorCode;
import com.fitwallet.domain.payment.mapper.PaymentMapper;
import com.fitwallet.global.exception.BusinessException;
import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import com.fitwallet.domain.payment.dto.PinAuthInfo;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

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

    @InjectMocks
    private DefaultPaymentService paymentService;

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
}