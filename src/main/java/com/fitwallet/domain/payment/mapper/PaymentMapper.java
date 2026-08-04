package com.fitwallet.domain.payment.mapper;

import com.fitwallet.domain.payment.dto.PaymentSessionStatus;
import com.fitwallet.domain.payment.dto.PinAuthInfo;
import com.fitwallet.domain.payment.dto.UserPinInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
