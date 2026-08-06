package com.fitwallet.domain.user.mapper;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.UserLoginInfoResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사용자 도메인의 데이터 접근을 담당한다.
 * <p>
 * 회원가입 시 아이디 중복 여부를 확인하고 {@code users} 테이블에
 * 사용자 정보를 저장한다.
 */
@Mapper
public interface UserMapper {

    /** 동일한 로그인 아이디가 존재하는지 확인한다. */
    boolean existsByLoginId(@Param("loginId") String loginId);

    /**
     * 회원가입 요청 정보를 사용자 테이블에 저장한다.
     * 비밀번호는 서비스에서 암호화한 값을 전달한다.
     *
     * @param request 회원가입 요청 정보
     * @param encodedPassword 암호화된 비밀번호
     */
    void insertUser(@Param("request") SignUpRequest request,
                    @Param("encodedPassword") String encodedPassword);

    /** 로그인 아이디로 사용자 식별자와 비밀번호 해시를 조회한다. */
    UserLoginInfoResponse findLoginInfoByLoginId(@Param("loginId") String loginId);

    /**
     * 유저의 리프레시 토큰 해시를 저장한다.
     * 유저당 활성 토큰은 하나뿐이라({@code refresh_token.user_id} UNIQUE),
     * 이미 있으면 갱신하고 없으면 새로 만든다.
     */
    void saveOrUpdateRefreshToken(@Param("userId") Long userId, @Param("tokenHash") String tokenHash);

    /**
     * 최근 1개월 결제내역을 가맹점 기준으로 집계해, 결제 건수 내림차순(동률이면 최근 결제일
     * 내림차순)으로 최대 3개만 조회한다. 결제내역이 없으면 빈 리스트를 반환한다.
     */
    List<FrequentPlaceResponse> findFrequentPlaces(@Param("userId") Long userId);

    /**
     * 유저에게 저장된 리프레시 토큰 해시를 조회한다.
     * 저장된 토큰이 없으면 {@code null}을 반환한다.
     */
    String findRefreshTokenHashByUserId(@Param("userId") Long userId);

    /** 결제 PIN 해시를 저장한다. 이미 등록돼 있어도 덮어쓴다. PIN은 서비스에서 암호화한 값을 전달한다. */
    void registerPaymentPin(@Param("userId") Long userId, @Param("pinHash") String pinHash);

}
