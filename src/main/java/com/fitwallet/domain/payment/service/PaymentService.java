package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.payment.dto.PaymentApproveResult;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.request.StoreQrScanRequest;
import com.fitwallet.domain.payment.dto.response.*;

public interface PaymentService {

    PinVerifyResponse verifyPin(Long userId, PinVerifyRequest request);

    QrGenerateResponse generateQr(Long userId, QrGenerateRequest request);

    QrStatusResponse getQrStatus(Long userId, String qrToken);

    PaymentResultResponse getPaymentResult(Long userId, String paymentId);

    StoreQrScanResponse scanStoreQr(Long userId, StoreQrScanRequest request);

    PaymentApproveResult approvePayment(Long userId, String paymentId);
}
