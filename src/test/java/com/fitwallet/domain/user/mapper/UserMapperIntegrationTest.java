package com.fitwallet.domain.user.mapper;

import com.fitwallet.domain.user.dto.request.SignUpRequest;
import com.fitwallet.domain.user.dto.response.FrequentPlaceResponse;
import com.fitwallet.domain.user.dto.response.UserInfoResponse;
import com.fitwallet.domain.user.dto.response.UserLoginInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class UserMapperIntegrationTest {

    private static final String NEW_LOGIN_ID = "signup-test-user";
    private static final String PASSWORD_HASH = "$2a$10$testPasswordHash";
    private static final String TOKEN_HASH_A = "a".repeat(64);
    private static final String TOKEN_HASH_B = "b".repeat(64);
    private static final String PIN_HASH = "$2a$10$testPinHash";
    private static final String PIN_HASH_B = "$2a$10$testPinHashB";

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
    void 존재하지_않는_아이디로_로그인정보를_조회하면_null을_반환한다() {
        assertThat(userMapper.findLoginInfoByLoginId(NEW_LOGIN_ID)).isNull();
    }

    @Test
    void 존재하는_아이디로_로그인정보를_조회하면_사용자ID와_비밀번호해시를_반환한다() {
        userMapper.insertUser(signUpRequest(), PASSWORD_HASH);

        UserLoginInfoResponse loginInfo = userMapper.findLoginInfoByLoginId(NEW_LOGIN_ID);

        assertThat(loginInfo.getUserId()).isNotNull();
        assertThat(loginInfo.getPasswordHash()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    void 기존_리프레시_토큰이_없으면_새로_저장한다() {
        Long userId = registerAndGetUserId();

        userMapper.saveOrUpdateRefreshToken(userId, TOKEN_HASH_A);

        String savedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE user_id = ?",
                String.class,
                userId
        );

        assertThat(savedHash).isEqualTo(TOKEN_HASH_A);
    }

    @Test
    void 리프레시_토큰을_다시_저장하면_해시만_갱신한다() {
        Long userId = registerAndGetUserId();

        userMapper.saveOrUpdateRefreshToken(userId, TOKEN_HASH_A);
        userMapper.saveOrUpdateRefreshToken(userId, TOKEN_HASH_B);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ?",
                Integer.class,
                userId
        );
        String savedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE user_id = ?",
                String.class,
                userId
        );

        assertThat(count).isEqualTo(1);
        assertThat(savedHash).isEqualTo(TOKEN_HASH_B);
    }

    @Test
    void 저장된_리프레시_토큰_해시를_조회한다() {
        Long userId = registerAndGetUserId();
        userMapper.saveOrUpdateRefreshToken(userId, TOKEN_HASH_A);

        String tokenHash = userMapper.findRefreshTokenHashByUserId(userId);

        assertThat(tokenHash).isEqualTo(TOKEN_HASH_A);
    }

    @Test
    void 저장된_리프레시_토큰이_없으면_null을_반환한다() {
        Long userId = registerAndGetUserId();

        assertThat(userMapper.findRefreshTokenHashByUserId(userId)).isNull();
    }

    @Test
    void 리프레시_토큰을_삭제하면_더이상_조회되지_않는다() {
        Long userId = registerAndGetUserId();
        userMapper.saveOrUpdateRefreshToken(userId, TOKEN_HASH_A);

        userMapper.deleteRefreshToken(userId);

        assertThat(userMapper.findRefreshTokenHashByUserId(userId)).isNull();
    }

    @Test
    void 최초_등록시_UPDATE_결과가_1이고_해시가_그대로_조회된다() {
        Long userId = registerAndGetUserId();

        int updatedRows = userMapper.registerPaymentPin(userId, PIN_HASH);

        String savedHash = jdbcTemplate.queryForObject(
                "SELECT payment_pin_hash FROM users WHERE user_id = ?",
                String.class,
                userId
        );
        assertThat(updatedRows).isEqualTo(1);
        assertThat(savedHash).isEqualTo(PIN_HASH);
    }

    @Test
    void 이미_등록된_PIN은_UPDATE_결과가_0이고_기존_해시가_유지된다() {
        Long userId = registerAndGetUserId();
        userMapper.registerPaymentPin(userId, PIN_HASH);

        int updatedRows = userMapper.registerPaymentPin(userId, PIN_HASH_B);

        String savedHash = jdbcTemplate.queryForObject(
                "SELECT payment_pin_hash FROM users WHERE user_id = ?",
                String.class,
                userId
        );
        assertThat(updatedRows).isEqualTo(0);
        assertThat(savedHash).isEqualTo(PIN_HASH);
    }

    @Test
    void PIN을_등록한_적_없으면_해시_조회는_null이다() {
        Long userId = registerAndGetUserId();

        assertThat(userMapper.findPaymentPinHash(userId)).isNull();
    }

    @Test
    void 등록된_PIN_해시를_그대로_조회한다() {
        Long userId = registerAndGetUserId();
        userMapper.registerPaymentPin(userId, PIN_HASH);

        assertThat(userMapper.findPaymentPinHash(userId)).isEqualTo(PIN_HASH);
    }

    @Test
    void PIN을_변경하면_등록된_상태여도_해시가_덮어써진다() {
        Long userId = registerAndGetUserId();
        userMapper.registerPaymentPin(userId, PIN_HASH);

        userMapper.updatePaymentPin(userId, PIN_HASH_B);

        assertThat(userMapper.findPaymentPinHash(userId)).isEqualTo(PIN_HASH_B);
    }

    @Test
    void 마이페이지_조회는_이름을_그대로_반환한다() {
        Long userId = registerAndGetUserId();

        UserInfoResponse userInfo = userMapper.findUserInfo(userId);

        assertThat(userInfo.getName()).isEqualTo("회원가입테스트");
    }

    @Test
    void 존재하지_않는_사용자의_마이페이지_조회는_null이다() {
        assertThat(userMapper.findUserInfo(9999L)).isNull();
    }

    @Test
    void 위치_정보_동의로_저장하면_컬럼이_true가_된다() {
        Long userId = registerAndGetUserId();

        userMapper.updateLocationAgreement(userId, true);

        Boolean agreed = jdbcTemplate.queryForObject(
                "SELECT is_location_agreed FROM users WHERE user_id = ?",
                Boolean.class,
                userId
        );
        assertThat(agreed).isTrue();
    }

    private Long registerAndGetUserId() {
        userMapper.insertUser(signUpRequest(), PASSWORD_HASH);
        return userMapper.findLoginInfoByLoginId(NEW_LOGIN_ID).getUserId();
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

    @Test
    void 결제_건수가_많은_순서로_최대_3개까지_조회하고_장소_정보를_함께_채운다() {
        Long userId = createUserWithCard("frequent-count-user");
        Long userCardId = findUserCardId(userId);
        Long storeA = createStore("A매장");
        Long storeB = createStore("B매장");
        Long storeC = createStore("C매장");
        Long storeD = createStore("D매장");
        Long storeE = createStore("E매장");
        insertTransactions(userCardId, storeA, 4);
        insertTransactions(userCardId, storeB, 3);
        insertTransactions(userCardId, storeC, 5);
        insertTransactions(userCardId, storeD, 2);
        insertTransactions(userCardId, storeE, 1);

        List<FrequentPlaceResponse> places = userMapper.findFrequentPlaces(userId);

        assertThat(places).extracting(FrequentPlaceResponse::getStoreId)
                .containsExactly(storeC, storeA, storeB);

        FrequentPlaceResponse topPlace = places.get(0);
        assertThat(topPlace.getStoreName()).isEqualTo("C매장");
        assertThat(topPlace.getAddress()).isEqualTo("서울시 테스트구");
        assertThat(topPlace.getCategoryName()).isEqualTo("카페/디저트");
    }

    @Test
    void 결제_건수가_같으면_가장_최근_결제일이_늦은_장소를_우선한다() {
        Long userId = createUserWithCard("frequent-tie-user");
        Long userCardId = findUserCardId(userId);
        Long olderStore = createStore("먼저방문한매장");
        Long recentStore = createStore("최근방문한매장");
        insertTransactionDaysAgo(userCardId, olderStore, 20);
        insertTransactionDaysAgo(userCardId, olderStore, 10);
        insertTransactionDaysAgo(userCardId, recentStore, 15);
        insertTransactionDaysAgo(userCardId, recentStore, 5);

        List<FrequentPlaceResponse> places = userMapper.findFrequentPlaces(userId);

        assertThat(places).extracting(FrequentPlaceResponse::getStoreId)
                .containsExactly(recentStore, olderStore);
    }

    /**
     * 조회 조건 2가지(최근 1개월·본인 소유)를 한 테스트로 묶어 검증한다. 무효장소에는
     * 조건을 하나씩만 어긋나는 결제(1개월 밖 / 다른 사용자)만 넣어 둘 다 걸러지면 결과에
     * 아예 나타나지 않는다는 사실로 증명하고, 유효장소의 정상 결제 1건이 여전히 조회되는
     * 것으로 정상 경로가 안 깨졌는지 함께 본다.
     */
    @Test
    void 최근_1개월_안에_본인이_결제한_내역만_집계한다() {
        Long userId = createUserWithCard("frequent-scope-user");
        Long userCardId = findUserCardId(userId);
        Long otherUserId = createUserWithCard("frequent-scope-other-user");
        Long otherUserCardId = findUserCardId(otherUserId);
        Long validStore = createStore("유효장소");
        Long invalidStore = createStore("무효장소");

        insertTransactionDaysAgo(userCardId, validStore, 10);
        insertTransactionDaysAgo(userCardId, invalidStore, 60);
        insertTransactionDaysAgo(otherUserCardId, invalidStore, 1);

        List<FrequentPlaceResponse> places = userMapper.findFrequentPlaces(userId);

        assertThat(places).extracting(FrequentPlaceResponse::getStoreId)
                .containsExactly(validStore);
    }

    /** 회원가입 후 카드 상품(card_product_id=1)으로 카드 한 장을 등록한다. */
    private Long createUserWithCard(String loginId) {
        SignUpRequest request = new SignUpRequest();
        ReflectionTestUtils.setField(request, "name", "자주찾는장소테스트");
        ReflectionTestUtils.setField(request, "loginId", loginId);
        ReflectionTestUtils.setField(request, "phone", "01012345678");
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "passwordConfirm", "password123");
        ReflectionTestUtils.setField(request, "marketingAgreed", false);
        userMapper.insertUser(request, PASSWORD_HASH);
        Long userId = userMapper.findLoginInfoByLoginId(loginId).getUserId();

        jdbcTemplate.update(
                "INSERT INTO user_card (user_id, card_product_id, first4, last4, expiry_date, "
                        + "display_order, is_deleted) VALUES (?, 1, '1234', '5678', '2030-01-01', 1, 0)",
                userId
        );

        return userId;
    }

    private Long findUserCardId(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_card_id FROM user_card WHERE user_id = ? AND is_deleted = 0",
                Long.class,
                userId
        );
    }

    /** category_id=1(카페/디저트)에 속한 매장을 하나 만든다. */
    private Long createStore(String storeName) {
        jdbcTemplate.update(
                "INSERT INTO store (category_id, store_name, address) VALUES (1, ?, '서울시 테스트구')",
                storeName
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertTransactions(Long userCardId, Long storeId, int count) {
        for (int i = 0; i < count; i++) {
            insertTransactionDaysAgo(userCardId, storeId, i);
        }
    }

    private void insertTransactionDaysAgo(Long userCardId, Long storeId, int daysAgo) {
        jdbcTemplate.update(
                "INSERT INTO payment_transaction (user_card_id, store_id, amount, final_amount, paid_at) "
                        + "VALUES (?, ?, 1000, 1000, NOW() - INTERVAL ? DAY)",
                userCardId, storeId, daysAgo
        );
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
