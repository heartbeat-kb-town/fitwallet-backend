package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.mapper.StoreMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * Service 단위 테스트. Mapper를 목킹하므로 DB가 필요 없다.
 * <p>
 * {@code @InjectMocks}는 구체 클래스가 있어야 인스턴스를 만들 수 있어 필드 타입을
 * 인터페이스({@code SearchHistoryService})가 아니라 구현체로 둔다.
 * <p>
 * ⚠️ {@code REQUIRES_NEW} propagation은 여기서 검증할 수 없다 — Mockito는 트랜잭션 프록시를
 * 만들지 않는다. 애너테이션 값을 리플렉션으로 단정하는 테스트는 값 복사에 불과해 회귀를 못 잡으므로
 * 쓰지 않는다. 실제 격리 효과는 조회 서비스가 생긴 뒤(#30) "기록이 실패해도
 * {@code GET /api/store/search}가 200을 반환한다"는 컨트롤러 테스트로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSearchHistoryServiceTest {

    @Mock
    private StoreMapper storeMapper;

    @InjectMocks
    private DefaultSearchHistoryService searchHistoryService;

    @Test
    void 검색어를_기록하면_매퍼_upsert를_한_번_호출한다() {
        searchHistoryService.record(1L, "스타벅스");

        then(storeMapper).should().upsertSearchHistory(1L, "스타벅스");
    }

    @Test
    void 키워드가_null이면_매퍼를_호출하지_않는다() {
        searchHistoryService.record(1L, null);

        then(storeMapper).shouldHaveNoInteractions();
    }

    @Test
    void 키워드가_공백뿐이면_매퍼를_호출하지_않는다() {
        searchHistoryService.record(1L, "   ");

        then(storeMapper).shouldHaveNoInteractions();
    }

    // 명세의 "실패해도 200"은 이 서비스가 예외를 삼켜서가 아니라 호출부의 try-catch로 지켜진다.
    // 여기서 삼키면 트랜잭션 시작 단계의 실패(커넥션 풀 고갈)는 어차피 못 막으면서
    // "다 막았다"는 착각만 준다. 그 계약을 이 테스트가 고정한다.
    @Test
    void 기록에_실패하면_예외를_그대로_던진다() {
        willThrow(new DataIntegrityViolationException("fk 위반"))
                .given(storeMapper).upsertSearchHistory(any(), anyString());

        assertThatThrownBy(() -> searchHistoryService.record(9999L, "없는사용자"))
                .isInstanceOf(DataAccessException.class);
    }

    // 정규화는 조회 서비스의 책임이다. 여기서 다듬으면 조회에 쓴 키워드와 저장된 키워드가 달라진다.
    @Test
    void 키워드_앞뒤_공백을_그대로_저장한다() {
        searchHistoryService.record(1L, " 카페 ");

        then(storeMapper).should().upsertSearchHistory(1L, " 카페 ");
    }
}
