package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.exception.UserErrorCode;
import com.fitwallet.domain.user.mapper.UserMapper;
import com.fitwallet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultUserService implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 일반 회원가입을 처리한다.
     * <p>
     * 비밀번호와 비밀번호 확인값의 일치 여부를 검증하고,
     * 로그인 아이디가 중복되지 않은 경우 비밀번호를 암호화해 저장한다.
     */
    @Override
    @Transactional
    public void signUp(SignUpRequest request) {
        validatePasswordConfirmation(request);

        if (userMapper.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_LOGIN_ID);
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        userMapper.insertUser(request, passwordHash);
    }

    /** 비밀번호와 비밀번호 확인값이 일치하는지 검증한다. */
    private void validatePasswordConfirmation(SignUpRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }
    }
}