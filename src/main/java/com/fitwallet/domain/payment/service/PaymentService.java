package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;
import com.fitwallet.domain.payment.dto.response.QrStatusResponse;

public interface PaymentService {

    PinVerifyResponse verifyPin(Long userId, PinVerifyRequest request);

    QrGenerateResponse generateQr(Long userId, QrGenerateRequest request);

    QrStatusResponse getQrStatus(Long userId, String qrToken);
}
