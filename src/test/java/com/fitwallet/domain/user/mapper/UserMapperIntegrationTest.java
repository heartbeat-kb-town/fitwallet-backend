package com.fitwallet.domain.user.mapper;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class UserMapperIntegrationTest {

    private static final String NEW_LOGIN_ID = "signup-test-user";
    private static final String PASSWORD_HASH = "$2a$10$testPasswordHash";

    @Autowired
    private UserMapper userMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 존재하지_않는_아이디는_중복이_아니다() {
        assertThat(userMapper.existsByLoginId(NEW_LOGIN_ID)).isFalse();
    }

    @Test
    void 회원을_등록하면_아이디가_존재한다() {
        userMapper.insertUser(signUpRequest(), PASSWORD_HASH);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login_id = ?",
                Integer.class,
                NEW_LOGIN_ID
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void 회원을_등록하면_비밀번호해시와_마케팅동의가_저장된다() {
        userMapper.insertUser(signUpRequest(), PASSWORD_HASH);

        String savedPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?",
                String.class,
                NEW_LOGIN_ID
        );

        Boolean marketingAgreed = jdbcTemplate.queryForObject(
                "SELECT is_marketing_agreed FROM users WHERE login_id = ?",
                Boolean.class,
                NEW_LOGIN_ID
        );

        assertThat(savedPasswordHash).isEqualTo(PASSWORD_HASH);
        assertThat(marketingAgreed).isTrue();
    }

    private SignUpRequest signUpRequest() {
        SignUpRequest request = new SignUpRequest();
        ReflectionTestUtils.setField(request, "name", "회원가입테스트");
        ReflectionTestUtils.setField(request, "loginId", NEW_LOGIN_ID);
        ReflectionTestUtils.setField(request, "phone", "01012345678");
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "passwordConfirm", "password123");
        ReflectionTestUtils.setField(request, "marketingAgreed", true);
        return request;
    }
}