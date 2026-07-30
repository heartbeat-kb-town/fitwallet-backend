package com.fitwallet.domain.user.mapper;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}