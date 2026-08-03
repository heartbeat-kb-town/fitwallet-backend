package com.fitwallet.domain.payment.service;

import com.fitwallet.domain.card.exception.CardErrorCode;
import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import com.fitwallet.domain.payment.dto.PinAuthInfo;
import com.fitwallet.domain.payment.dto.UserPinInfo;
import com.fitwallet.domain.payment.dto.request.PinVerifyRequest;
import com.fitwallet.domain.payment.dto.request.QrGenerateRequest;
import com.fitwallet.domain.payment.dto.response.PinMismatchResponse;
import com.fitwallet.domain.payment.dto.response.PinVerifyResponse;
import com.fitwallet.domain.payment.dto.response.QrGenerateResponse;
import com.fitwallet.domain.payment.exception.PaymentErrorCode;
import com.fitwallet.domain.payment.mapper.PaymentMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final int PIN_AUTH_TTL_SECONDS = 180;
    private static final int QR_SESSION_TTL_SECONDS = 180;

    private final PaymentMapper paymentMapper;
    private final PasswordEncoder passwordEncoder;

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
            throw new BusinessException(PaymentErrorCode.AUTH_ID_INVALID);
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
}
