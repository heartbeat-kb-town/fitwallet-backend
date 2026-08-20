package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardType;
import com.fitwallet.domain.card.dto.CardListSortType;
import com.fitwallet.domain.card.dto.MyDataCard;
import com.fitwallet.domain.card.dto.MyDataTransaction;
import com.fitwallet.domain.card.dto.request.CardRegisterRequest;
import com.fitwallet.domain.card.dto.request.CardTransactionSearchCondition;
import com.fitwallet.domain.card.dto.response.CardListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<CardListResponse> cards = cardMapper.findByUserId(
                SEED_USER_ID, CardListSortType.DISPLAY_ORDER, paymentCondition());

        assertThat(cards).hasSize(9)
                .isSortedAccordingTo(Comparator.comparing(CardListResponse::getDisplayOrder));
    }

    @Test
    void 조인한_카드상품과_카드사_정보가_함께_채워진다() {
        List<CardListResponse> cards = cardMapper.findByUserId(
                SEED_USER_ID, CardListSortType.DISPLAY_ORDER, paymentCondition());

        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getCardName()).isNotBlank();
            assertThat(card.getCardProductId()).isNotNull();
            assertThat(card.getCardType()).isNotNull();
            assertThat(card.getIssuerName()).isNotBlank();
        });

        // card_image_url은 nullable이라 allSatisfy로 묶을 수 없다. 여기서 보는 것은
        // "매퍼 XML이 이 컬럼을 SELECT하고 매핑하는가" 하나뿐이다 — resultType 매핑은
        // SQL이 안 뽑은 컬럼을 조용히 버리므로(AGENTS.md §6) 이 단언이 그걸 잡는다.
        //
        // 반대로 "이미지가 null인 카드가 있다"는 단언은 두지 않는다. 어느 카드에 이미지를
        // 넣을지는 제품 결정이라 시드가 바뀌면 함께 바뀌고, 실제로 #119가 신한카드 20종을
        // 채우면서 그 단언이 깨져 develop이 빨개졌다(#129). 매퍼 테스트는 매퍼만 본다.
        assertThat(cards).filteredOn(c -> c.getCardImageUrl() != null).isNotEmpty();
    }

    @Test
    void CHECK_제약_문자열이_CardType_enum으로_변환된다() {
        List<CardListResponse> cards = cardMapper.findByUserId(
                SEED_USER_ID, CardListSortType.DISPLAY_ORDER, paymentCondition());

        assertThat(cards).extracting(CardListResponse::getCardType)
                .containsAnyOf(CardType.CREDIT, CardType.DEBIT)
                .doesNotContainNull();
    }

    @Test
    void 신용카드는_한도가_체크카드는_잔액이_BigDecimal로_채워진다() {
        List<CardListResponse> cards = cardMapper.findByUserId(
                SEED_USER_ID, CardListSortType.DISPLAY_ORDER, paymentCondition());

        assertThat(cards).filteredOn(c -> c.getCardType() == CardType.CREDIT)
                .isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.getCreditLimit()).isNotNull();
                    assertThat(c.getScheduledPaymentAmount()).isNotNull();
                });

        assertThat(cards).filteredOn(c -> c.getCardType() == CardType.DEBIT)
                .isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.getBalance()).isNotNull();
                    assertThat(c.getScheduledPaymentAmount()).isNull();
                });
    }

    @Test
    void 소프트_삭제된_카드는_목록에서_제외된다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findByUserId(
                SEED_USER_ID, CardListSortType.DISPLAY_ORDER, paymentCondition())).hasSize(8)
                .extracting(CardListResponse::getUserCardId)
                .doesNotContain(1L);
    }

    @Test
    void 소프트_삭제된_카드는_단건으로도_조회되지_않는다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findByUserIdAndUserCardId(
                SEED_USER_ID, 1L, paymentCondition())).isNull();
    }

    @Test
    void 다른_사용자의_카드는_조회되지_않는다() {
        assertThat(cardMapper.findByUserIdAndUserCardId(
                9999L, 1L, paymentCondition())).isNull();
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
        assertThat(cardMapper.findMaxDisplayOrder(SEED_USER_ID)).isEqualTo(9);
    }

    @Test
    void 표시순서_최대값은_삭제된_카드를_세지_않는다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE display_order = 9");

        assertThat(cardMapper.findMaxDisplayOrder(SEED_USER_ID)).isEqualTo(8);
    }

    @Test
    void 카드가_없는_사용자의_표시순서_최대값은_0이다() {
        assertThat(cardMapper.findMaxDisplayOrder(9999L)).isZero();
    }

    @Test
    void 카드를_등록하면_감사컬럼은_DB가_채우고_바로_조회된다() {
        CardRegisterRequest request = registerRequest(1L);

        cardMapper.insertUserCard(SEED_USER_ID, request, 6);

        CardListResponse saved = cardMapper.findByUserIdAndCardProductId(
                SEED_USER_ID, 1L, paymentCondition());
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

        CardListResponse revived = cardMapper.findByUserIdAndUserCardId(
                SEED_USER_ID, 1L, paymentCondition());
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

    @Test
    void 마이데이터_결제예정액은_원본으로_저장하되_API값은_승인거래로_재계산한다() {
        // card_product_id=1은 CREDIT이고 SEED_USER_ID(1)에는 아직 등록돼 있지 않다.
        MyDataCard card = myDataCard(1L, "9999", "1111", null, null,
                BigDecimal.valueOf(2_500_000), BigDecimal.valueOf(180_000), List.of());

        cardMapper.insertMyDataCard(SEED_USER_ID, card, 6, new HashMap<>());

        CardListResponse saved = cardMapper.findByUserIdAndCardProductId(
                SEED_USER_ID, 1L, paymentCondition());
        assertThat(saved).isNotNull();
        assertThat(saved.getMaskedFrontNumber()).isEqualTo("9999");
        assertThat(saved.getMaskedRearNumber()).isEqualTo("1111");
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2031, 12, 31));
        assertThat(saved.getCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(2_500_000));
        assertThat(saved.getScheduledPaymentAmount()).isZero();
        assertThat(saved.getBankName()).isNull();
        assertThat(saved.getBalance()).isNull();

        BigDecimal storedAmount = jdbcTemplate.queryForObject(
                "SELECT scheduled_payment_amount FROM user_card "
                        + "WHERE user_id = ? AND card_product_id = ?",
                BigDecimal.class,
                SEED_USER_ID,
                1L);
        assertThat(storedAmount).isEqualByComparingTo(BigDecimal.valueOf(180_000));
    }

    @Test
    void 생성된_userCardId로_거래내역이_연결되고_금액_가맹점_결제시각이_저장된다() {
        Long userCardId = insertMyDataCardAndGetUserCardId();
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 18, 15, 5);

        cardMapper.insertMyDataTransactions(userCardId, List.of(
                MyDataTransaction.builder()
                        .amount(BigDecimal.valueOf(8_500))
                        .storeId(1L)
                        .paidAt(paidAt)
                        .build()));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM payment_transaction WHERE user_card_id = ?", userCardId);

        assertThat(((Number) row.get("user_card_id")).longValue()).isEqualTo(userCardId);
        assertThat(((Number) row.get("store_id")).longValue()).isEqualTo(1L);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(BigDecimal.valueOf(8_500));
        assertThat((BigDecimal) row.get("final_amount")).isEqualByComparingTo(BigDecimal.valueOf(8_500));
        assertThat((LocalDateTime) row.get("paid_at")).isEqualTo(paidAt);
        assertThat(row.get("transaction_status")).isEqualTo("APPROVED");
    }

    /** card_product_id=1(CREDIT)로 마이데이터 카드를 등록하고 생성된 PK를 돌려준다. */
    private Long insertMyDataCardAndGetUserCardId() {
        MyDataCard card = myDataCard(1L, "9999", "2222", null, null,
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(50_000), List.of());
        Map<String, Object> keyHolder = new HashMap<>();

        cardMapper.insertMyDataCard(SEED_USER_ID, card, 6, keyHolder);

        // MySQL 드라이버가 생성된 BIGINT를 BigInteger로 돌려주므로 Number로 받는다
        Long userCardId = ((Number) keyHolder.get("userCardId")).longValue();
        assertThat(userCardId).isNotNull();
        return userCardId;
    }

    @Test
    void 보유_카드_ID만_삭제된_카드_없이_조회한다() {
        assertThat(cardMapper.findUserCardIds(SEED_USER_ID))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    void 소프트_삭제된_카드는_보유_카드_ID_조회에서_제외된다() {
        jdbcTemplate.update("UPDATE user_card SET is_deleted = 1 WHERE user_card_id = 1");

        assertThat(cardMapper.findUserCardIds(SEED_USER_ID))
                .containsExactlyInAnyOrder(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    void 표시순서를_변경하면_리스트_순서대로_1부터_채워지고_영향받은_행수를_반환한다() {
        int updatedRows = cardMapper.updateCardsDisplayOrder(SEED_USER_ID, List.of(3L, 1L, 5L, 2L, 4L));

        assertThat(updatedRows).isEqualTo(5);
        assertThat(displayOrderOf(3L)).isEqualTo(1);
        assertThat(displayOrderOf(1L)).isEqualTo(2);
        assertThat(displayOrderOf(5L)).isEqualTo(3);
        assertThat(displayOrderOf(2L)).isEqualTo(4);
        assertThat(displayOrderOf(4L)).isEqualTo(5);
    }

    private CardTransactionSearchCondition paymentCondition() {
        return CardTransactionSearchCondition.builder()
                .startAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .endAt(LocalDateTime.of(2100, 1, 1, 0, 0))
                .build();
    }

    private Integer displayOrderOf(Long userCardId) {
        return jdbcTemplate.queryForObject(
                "SELECT display_order FROM user_card WHERE user_card_id = ?",
                Integer.class, userCardId);
    }

    private MyDataCard myDataCard(Long cardProductId, String first4, String last4,
                                   String bankName, BigDecimal balance,
                                   BigDecimal creditLimit, BigDecimal scheduledPaymentAmount,
                                   List<MyDataTransaction> transactions) {
        return MyDataCard.builder()
                .cardProductId(cardProductId)
                .first4(first4)
                .last4(last4)
                .expiryDate(LocalDate.of(2031, 12, 31))
                .bankName(bankName)
                .balance(balance)
                .creditLimit(creditLimit)
                .scheduledPaymentAmount(scheduledPaymentAmount)
                .transactions(transactions)
                .build();
    }
}
