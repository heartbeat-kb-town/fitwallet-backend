package com.fitwallet.domain.payment.controller;

import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import com.fitwallet.domain.payment.dto.PaymentSuccessCode;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;
import com.fitwallet.domain.payment.dto.response.QrStatusResponse;
import com.fitwallet.domain.payment.service.PaymentService;
import com.fitwallet.global.common.annotation.LoginUserId;
import com.fitwallet.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payment/pin/verify")
    public ResponseEntity<ApiResponse<PinVerifyResponse>> verifyPin(@LoginUserId Long userId,
                                                                    @Valid @RequestBody PinVerifyRequest request){
        return ApiResponse.of(PaymentSuccessCode.PIN_VERIFIED,
                paymentService.verifyPin(userId, request));
    }

    @PostMapping("/payment/qr")
    public ResponseEntity<ApiResponse<QrGenerateResponse>> generateQr(@LoginUserId Long userId,
                                                                      @Valid @RequestBody QrGenerateRequest request){
        return ApiResponse.of(PaymentSuccessCode.QR_CREATED,
                paymentService.generateQr(userId, request));
    }

    @GetMapping("/payment/qr/{qrToken}/status")
    public ResponseEntity<ApiResponse<QrStatusResponse>> getQrStatus(@LoginUserId Long userId,
                                                                     @PathVariable String qrToken){
        QrStatusResponse response = paymentService.getQrStatus(userId, qrToken);
        PaymentSuccessCode successCode = response.getStatus() == PaymentSessionStatus.SCANNED ?
                PaymentSuccessCode.QR_STATUS_SCANNED : PaymentSuccessCode.QR_STATUS_CREATED;
        return ApiResponse.of(successCode, response);
    }
}
