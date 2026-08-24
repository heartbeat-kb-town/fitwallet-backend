package com.fitwallet.domain.card.mapper;

import com.fitwallet.domain.card.dto.CardEventTargetType;
import com.fitwallet.domain.card.dto.response.CardEventItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 카드별 진행 중 이벤트 조회 Mapper 통합 테스트. */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class CardEventMapperIntegrationTest {

    private static final Long SEED_USER_ID = 1L;
    private static final Long SEED_USER_CARD_ID = 1L;

    @Autowired
    private CardMapper cardMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp(@Autowired DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // 시드 유저의 카드 1(KB국민 청춘대로 톡톡, card_product 47 / issuer 3)에 걸리는 이벤트는
    // 카드 지정 8번(2026-08-01~08-31)과 카드사 지정 5·9번(~08-31)·10번(~12-31)이다.
    // 기준일을 바꿔 가며 경계를 확인한다. V13이 허구 이벤트 3번을 지웠으므로 여기 없다.

    @Test
    void 진행중_이벤트를_카드상품과_카드사_대상으로_조회하고_종료일순으로_정렬한다() {
        List<CardEventItemResponse> events = cardMapper.findCardEventItems(
                SEED_USER_ID, SEED_USER_CARD_ID, LocalDate.of(2026, 8, 15));

        // 종료일이 같은 8·9·5는 시작일 내림차순(08-01, 08-01, 07-01), 8·9는 다시 ID 오름차순
        assertThat(events).extracting(CardEventItemResponse::getEventId)
                .containsExactly(8L, 9L, 5L, 10L);
        assertThat(events).extracting(CardEventItemResponse::getTargetType)
                .containsExactly(CardEventTargetType.CARD_PRODUCT, CardEventTargetType.ISSUER,
                        CardEventTargetType.ISSUER, CardEventTargetType.ISSUER);
        assertThat(events).extracting(CardEventItemResponse::getDaysRemaining)
                .containsExactly(16L, 16L, 16L, 138L);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getSummary()).isNotBlank();
            assertThat(event.getDetailAvailable()).isTrue();
        });
    }

    @Test
    void 종료일_당일_이벤트를_포함한다() {
        List<CardEventItemResponse> events = cardMapper.findCardEventItems(
                SEED_USER_ID, SEED_USER_CARD_ID, LocalDate.of(2026, 8, 31));

        assertThat(events).extracting(CardEventItemResponse::getEventId)
                .containsExactly(8L, 9L, 5L, 10L);
        assertThat(events.get(0).getDaysRemaining()).isZero();
    }

    @Test
    void 종료된_이벤트는_조회하지_않는다() {
        // 08-31에 끝난 5·8·9가 빠지고 연말까지 가는 10번만 남는다
        List<CardEventItemResponse> events = cardMapper.findCardEventItems(
                SEED_USER_ID, SEED_USER_CARD_ID, LocalDate.of(2026, 9, 1));

        assertThat(events).extracting(CardEventItemResponse::getEventId)
                .containsExactly(10L);
        assertThat(events.get(0).getDaysRemaining()).isEqualTo(121L);
    }

    @Test
    void 같은_종료일은_시작일_내림차순과_이벤트_ID_오름차순으로_정렬한다() {
        insertEvent(100L, "2026-07-10", "2026-08-31", "상품 이벤트 100");
        insertEvent(101L, "2026-07-20", "2026-08-31", "상품 이벤트 101");

        List<CardEventItemResponse> events = cardMapper.findCardEventItems(
                SEED_USER_ID, SEED_USER_CARD_ID, LocalDate.of(2026, 7, 24));

        // 08-31 종료 묶음이 시작일 내림차순(07-20, 07-10, 07-01)으로 서고 10번이 뒤에 붙는다
        assertThat(events).extracting(CardEventItemResponse::getEventId)
                .containsExactly(101L, 100L, 5L, 10L);
    }

    @Test
    void 다른_사용자의_카드는_이벤트를_조회하지_않는다() {
        assertThat(cardMapper.findCardEventItems(
                9999L, SEED_USER_CARD_ID, LocalDate.of(2026, 7, 24)))
                .isEmpty();
    }

    @Test
    void 삭제된_카드는_이벤트를_조회하지_않는다() {
        jdbcTemplate.update(
                "UPDATE user_card SET is_deleted = 1 WHERE user_card_id = ?",
                SEED_USER_CARD_ID);

        assertThat(cardMapper.findCardEventItems(
                SEED_USER_ID, SEED_USER_CARD_ID, LocalDate.of(2026, 7, 24)))
                .isEmpty();
    }

    private void insertEvent(Long eventId, String startsAt, String endsAt, String summary) {
        jdbcTemplate.update(
                "INSERT INTO card_event "
                        + "(event_id, card_product_id, issuer_id, summary, starts_at, ends_at, detail_url) "
                        + "VALUES (?, 47, NULL, ?, ?, ?, 'https://card.kbcard.com/')",
                eventId, summary, startsAt, endsAt);
    }
}
