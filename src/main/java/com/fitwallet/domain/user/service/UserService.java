package com.fitwallet.domain.user.service;

import com.fitwallet.domain.user.dto.request.SignUpRequest;

public interface UserService {
    void signUp(SignUpRequest request);
}
