package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;

public interface PaymentService {

    PinVerifyResponse verifyPin(Long userId, PinVerifyRequest request);

    QrGenerateResponse generateQr(Long userId, QrGenerateRequest request);
}
