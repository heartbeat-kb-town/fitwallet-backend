package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.UserPinInfo;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.exception.PaymentErrorCode;
import com.fitwallet.domain.payment.mapper.PaymentMapper;
import com.fitwallet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

    private PinVerifyRequest verifyRequest(String pin) {
        PinVerifyRequest request = new PinVerifyRequest();
        ReflectionTestUtils.setField(request, "userCardId", 1L);
        ReflectionTestUtils.setField(request, "paymentPin", pin);
        return request;
    }
}