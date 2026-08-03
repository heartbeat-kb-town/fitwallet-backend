package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;

public interface PaymentService {

    PinVerifyResponse verifyPin(Long userId, PinVerifyRequest request);
}
