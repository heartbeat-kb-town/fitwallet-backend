package com.fitwallet.domain.store.service;

import com.fitwallet.domain.store.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색어 기록 구현체.
 * <p>
 * 매퍼는 도메인당 하나라(§3) {@code search_history} SQL도 {@link StoreMapper}에 들어 있다.
 * 서비스만 둘({@code StoreService}, {@code SearchHistoryService})로 갈리고 매퍼는 하나인 비대칭은
 * 의도된 것이다 — 새 {@code SearchHistoryMapper}를 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DefaultSearchHistoryService implements SearchHistoryService {

    private final StoreMapper storeMapper;

    /**
     * {@code REQUIRES_NEW}가 이 클래스의 존재 이유다. 기본값 {@code REQUIRED}로 두면 두 가지가
     * 동시에 깨진다.
     * <ol>
     *   <li>호출부인 조회 서비스가 {@code readOnly = true}(§8)라 그 커넥션은
     *       {@code SET SESSION TRANSACTION READ ONLY} 상태다 — Connector/J의
     *       {@code readOnlyPropagatesToServer} 기본값이 {@code true}라 드라이버가 서버까지 전파한다.
     *       그 세션의 INSERT는 MySQL 에러 1792({@code Cannot execute statement in a READ ONLY
     *       transaction})로 <b>매번</b> 실패한다. 간헐적 버그가 아니라 기능이 아예 동작하지 않는다.</li>
     *   <li>그 실패가 공유 트랜잭션에 rollback-only를 걸어, 호출부가 catch해도 커밋 시점에
     *       {@code UnexpectedRollbackException}이 나고 조회 응답까지 500이 된다.</li>
     * </ol>
     * {@code REQUIRES_NEW}는 바깥 트랜잭션을 suspend하고 새 커넥션으로 독립 트랜잭션을 연다.
     * readOnly를 상속하지 않고, 예외가 나도 {@code isNewTransaction()}이 참이라 자기 트랜잭션만
     * 롤백한다. 대가는 요청당 커넥션을 잠깐 2개 잡는 것이다(풀 10, {@code connectionTimeout=3000ms}).
     * <p>
     * 애너테이션을 아예 빼는 것도 격리가 아니다. mybatis-spring이 {@code TransactionSynchronizationManager}에서
     * 바깥 트랜잭션의 {@code SqlSessionHolder}를 찾아 그대로 재사용하므로 결과는 {@code REQUIRED}와 같다.
     * <p>
     * 인터페이스가 아니라 구현체 메서드에 붙인다(§8) — 지금은 JDK 동적 프록시라 인터페이스에 붙여도
     * 걸리지만, CGLIB으로 바뀌면 인터페이스의 애너테이션은 조용히 무시된다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String keyword) {
        // 명세: "keyword가 전달된 경우에만 기록합니다." 호출부도 keyword가 없으면 부르지 않지만,
        // 이 컴포넌트가 정책을 직접 소유해야 호출부가 조건을 잊어도 NOT NULL 위반으로 터지지 않는다.
        //
        // trim은 하지 않는다 — 검색어 정규화는 StoreSearchCondition이 문서화한 대로 조회 서비스의
        // 책임이고, 여기서 값을 몰래 바꾸면 조회에 쓴 키워드와 저장된 키워드가 달라진다.
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        storeMapper.upsertSearchHistory(userId, keyword);
    }
}
