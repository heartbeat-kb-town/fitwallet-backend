package com.fitwallet.domain.payment.mapper;

import com.fitwallet.domain.payment.dto.*;
import com.fitwallet.domain.payment.dto.response.PaymentResultResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * users 테이블에서 PIN 해시/실패횟수를 읽고,
 * 실패시 카운트 증가, 성공시 인증정보 기록
 * user_card 소유 여부 확인과 payment_session(QR 세션) 생성
 */
@Mapper
public interface PaymentMapper {
    UserPinInfo findUserPinInfo(@Param("userId") Long userId);
    void incrementPinFailCount(@Param("userId") Long userId);
    void issuePinAuth(@Param("userId") Long userId,
                      @Param("pinAuthId") String pinAuthId,
                      @Param("expiresAt") LocalDateTime expiresAt);

    PinAuthInfo findPinAuthInfo(@Param("userId") Long userId);
    boolean existsUserCard(@Param("userId") Long userId, @Param("userCardId") Long userCardId);
    void markPinAuthUsed(@Param("userId") Long userId);
    void insertPaymentSession(@Param("userCardId") Long userCardId,
                              @Param("sessionToken") String sessionToken,
                              @Param("status") PaymentSessionStatus status,
                              @Param("expiresAt") LocalDateTime expiresAt);

    QrSessionInfo findQrSessionByToken(@Param("userId") Long userId, @Param("qrToken") String qrToken);
    void markSessionScanned(@Param("qrToken") String qrToken, @Param("paymentId") String paymentId);
    void markSessionExpired(@Param("qrToken") String qrToken);

    PaymentResultSessionInfo findSessionByPaymentId(@Param("userId") Long userId, @Param("paymentId") String paymentId);
    void markSessionProcessing(@Param("paymentId") String paymentId, @Param("storeId") Long storeId, @Param("amount") BigDecimal amount);
    void markSessionFailed(@Param("paymentId") String paymentId);
    void markSessionCompleted(@Param("paymentId") String paymentId);

    /** 체크카드일 때만 방금 저장한 결제 거래를 기준으로 로컬 시연용 잔액을 차감한다. */
    int updateDebitCardBalanceAfterPayment(@Param("userId") Long userId,
                                           @Param("userCardId") Long userCardId,
                                           @Param("paymentSessionId") Long paymentSessionId);

    /**
     * @param discountAmount             적용 혜택의 <b>네이티브 단위</b> 혜택값(CASHBACK=원, ACCUMULATE=포인트 개수)
     * @param appliedTierId              한도 사용량 집계 키. 넣지 않으면 {@code BenefitMapper.findUsage}가
     *                                   이 결제를 영영 세지 못해 한도 잔여가 줄지 않는다
     * @param alternativeDiscountAmount  최선 대안 카드의 혜택값(<b>원화</b>)
     * @param missedAmount               놓친 금액(<b>원화</b>)
     */
    void insertPaymentTransaction(@Param("userCardId") Long userCardId,
                                  @Param("storeId") Long storeId,
                                  @Param("paymentSessionId") Long paymentSessionId,
                                  @Param("amount") BigDecimal amount,
                                  @Param("discountAmount") BigDecimal discountAmount,
                                  @Param("finalAmount") BigDecimal finalAmount,
                                  @Param("paidAt") LocalDateTime paidAt,
                                  @Param("appliedBenefitServiceId") Long appliedBenefitServiceId,
                                  @Param("appliedTierId") Long appliedTierId,
                                  @Param("betterUserCardId") Long betterUserCardId,
                                  @Param("alternativeDiscountAmount") BigDecimal alternativeDiscountAmount,
                                  @Param("missedAmount") BigDecimal missedAmount);
    PaymentResultResponse findPaymentResultBySessionId(@Param("paymentSessionId") Long paymentSessionId);

    StoreInfo findStoreByQrToken(@Param("storeQrToken") String storeQrToken);
    void insertScannedPaymentSession(@Param("userCardId") Long userCardId,
                                     @Param("sessionToken") String sessionToken,
                                     @Param("paymentId") String paymentId,
                                     @Param("storeId") Long storeId,
                                     @Param("amount") BigDecimal amount,
                                     @Param("expiresAt") LocalDateTime expiresAt);
}
