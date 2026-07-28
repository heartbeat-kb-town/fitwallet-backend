package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 * <p>
 * 클래스 레벨 {@code @Transactional}이 테스트마다 롤백하므로,
 * 데이터를 바꾸는 테스트를 써도 다음 테스트에 영향을 주지 않는다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardMapperIntegrationTest {

    /** 시드 데모 페르소나. user_card 5건을 갖고 있다. */
    private static final Long SEED_USER_ID = 1L;

    @Autowired
    private CardMapper cardMapper;

    /**
     * 테스트 데이터를 흔들기 위한 용도. DataSource가 트랜잭션에 묶여 있어
     * 여기서 바꾼 값도 테스트 종료 시 함께 롤백된다.
     */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void 사용자의_카드를_표시순서대로_조회한다() {
        List<CardListResponse> cards = cardMapper.findByUserId(SEED_USER_ID);

        assertThat(cards).hasSize(5)
                .isSortedAccordingTo(Comparator.comparing(CardListResponse::getDisplayOrder));
    }

    @Test
    void 조인한_카드상품과_카드사_정보가_함께_채워진다() {
        List<CardListResponse> cards = cardMapper.findByUserId(SEED_USER_ID);

        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getCardName()).isNotBlank();
            assertThat(card.getCardProductId()).isNotNull();
            assertThat(card.getCardType()).isNotNull();
        });
    }

    @Test
    void CHECK_제약_문자열이_CardType_enum으로_변환된다() {
        List<CardListResponse> cards = cardMapper.findByUserId(SEED_USER_ID);

        assertThat(cards).extracting(CardListResponse::getCardType)
                .containsAnyOf(CardType.CREDIT, CardType.DEBIT)
                .doesNotContainNull();
    }

    @Test
    void 신용카드는_한도가_체크카드는_잔액이_BigDecimal로_채워진다() {
        List<CardListResponse> cards = cardMapper.findByUserId(SEED_USER_ID);

        assertThat(cards).filteredOn(c -> c.getCardType() == CardType.CREDIT)
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.getCreditLimit()).isNotNull());

        assertThat(cards).filteredOn(c -> c.getCardType() == CardType.DEBIT)
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.getBalance()).isNotNull());
    }

    @Test
    void 소프트_삭제된_카드는_목록에서_제외된다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findByUserId(SEED_USER_ID)).hasSize(4)
                .extracting(CardListResponse::getUserCardId)
                .doesNotContain(1L);
    }

    @Test
    void 소프트_삭제된_카드는_단건으로도_조회되지_않는다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findByUserIdAndUserCardId(SEED_USER_ID, 1L)).isNull();
    }

    @Test
    void 다른_사용자의_카드는_조회되지_않는다() {
        assertThat(cardMapper.findByUserIdAndUserCardId(9999L, 1L)).isNull();
    }

    // 한 테스트 안에서 "변경 전 조회 → 변경 → 다시 조회"를 하면 안 된다.
    // 같은 트랜잭션은 같은 SqlSession이라 MyBatis 1차 캐시가 첫 결과를 돌려주고,
    // JdbcTemplate으로 바꾼 데이터는 MyBatis가 모르므로 캐시를 비우지 않는다.
    // 그래서 상태별로 테스트를 나눈다.

    @Test
    void 등록한_적_없는_카드상품은_등록이력_플래그가_null이다() {
        assertThat(cardMapper.findDeletedFlag(SEED_USER_ID, 1L)).isNull();
    }

    @Test
    void 사용중인_카드는_등록이력_플래그가_false다() {
        // user_card_id=1 → card_product_id=47
        assertThat(cardMapper.findDeletedFlag(SEED_USER_ID, 47L)).isFalse();
    }

    @Test
    void 소프트_삭제된_카드는_등록이력_플래그가_true다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findDeletedFlag(SEED_USER_ID, 47L)).isTrue();
    }

    @Test
    void 표시순서_최대값을_조회한다() {
        assertThat(cardMapper.findMaxDisplayOrder(SEED_USER_ID)).isEqualTo(5);
    }

    @Test
    void 표시순서_최대값은_삭제된_카드를_세지_않는다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE display_order = 5");

        assertThat(cardMapper.findMaxDisplayOrder(SEED_USER_ID)).isEqualTo(4);
    }

    @Test
    void 카드가_없는_사용자의_표시순서_최대값은_0이다() {
        assertThat(cardMapper.findMaxDisplayOrder(9999L)).isZero();
    }

    @Test
    void 카드를_등록하면_감사컬럼은_DB가_채우고_바로_조회된다() {
        CardRegisterRequest request = registerRequest(1L);

        cardMapper.insertUserCard(SEED_USER_ID, request, 6);

        CardListResponse saved = cardMapper.findByUserIdAndCardProductId(SEED_USER_ID, 1L);
        assertThat(saved).isNotNull();
        assertThat(saved.getMaskedFrontNumber()).isEqualTo("1234");
        assertThat(saved.getDisplayOrder()).isEqualTo(6);

        Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM user_card WHERE user_id = ? AND card_product_id = ?",
                Timestamp.class, SEED_USER_ID, 1L);
        assertThat(createdAt).isNotNull();
    }

    @Test
    void 삭제된_카드를_재활성화하면_다시_조회된다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        cardMapper.reactivateUserCard(SEED_USER_ID, registerRequest(47L), 6);

        CardListResponse revived = cardMapper.findByUserIdAndUserCardId(SEED_USER_ID, 1L);
        assertThat(revived).isNotNull();
        assertThat(revived.getMaskedFrontNumber()).isEqualTo("1234");
        assertThat(revived.getDisplayOrder()).isEqualTo(6);
    }

    private CardRegisterRequest registerRequest(Long cardProductId) {
        CardRegisterRequest request = new CardRegisterRequest();
        ReflectionTestUtils.setField(request, "cardProductId", cardProductId);
        ReflectionTestUtils.setField(request, "first4", "1234");
        ReflectionTestUtils.setField(request, "last4", "5678");
        ReflectionTestUtils.setField(request, "expiryDate", LocalDate.of(2030, 1, 31));
        return request;
    }
}
