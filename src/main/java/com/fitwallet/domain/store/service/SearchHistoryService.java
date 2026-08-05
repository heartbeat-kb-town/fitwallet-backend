package com.fitwallet.domain.store.service;

/**
 * 가맹점 조회의 부수효과인 검색어 기록. 구현체는 {@link DefaultSearchHistoryService}.
 * <p>
 * <b>왜 {@code StoreService}와 분리하는가.</b> 명세는 "이 기록은 조회 응답을 막지 않으며, 실패해도
 * 결과는 그대로 200으로 반환합니다"라고 규정한다. 조회 서비스가 자기 메서드 안에서 직접 기록하면
 * 자기호출이라 트랜잭션 프록시가 걸리지 않아 propagation을 지정할 방법이 아예 없다.
 * 그래서 별도 빈으로 분리하고 주입받아 호출한다.
 * <p>
 * <b>다만 빈 분리만으로는 격리되지 않는다.</b> {@code @Transactional} 기본 propagation은
 * {@code REQUIRED}라 별도 빈이어도 바깥 트랜잭션에 참여한다. 참여 중 예외가 나면 트랜잭션
 * 인터셉터가 공유 상태에 rollback-only를 걸어, 호출부가 catch해도 커밋 시점에
 * {@code UnexpectedRollbackException}이 터진다. 게다가 조회 서비스는 {@code readOnly = true}라
 * 그 커넥션은 이미 {@code SET SESSION TRANSACTION READ ONLY} 상태이고, INSERT는 MySQL 에러 1792로
 * <b>매번</b> 실패한다. 그래서 구현체는 {@code REQUIRES_NEW}를 쓴다. 자세한 근거는 구현체 참고.
 *
 * <h2>호출부 계약</h2>
 * <b>{@link #record(Long, String)}은 예외를 그대로 던진다. 호출부가 반드시 try-catch로 감싸야 한다.</b>
 * <pre>
 * try {
 *     searchHistoryService.record(userId, keyword);
 * } catch (RuntimeException e) {
 *     log.warn("검색어 기록 실패. 조회 결과는 그대로 반환한다. userId={}, keyword={}", userId, keyword, e);
 * }
 * </pre>
 * 이 try-catch를 구현체 안으로 넣지 않는 이유는, {@code REQUIRES_NEW}가 <b>메서드 본문 진입 전에</b>
 * 새 커넥션을 얻기 때문이다. 커넥션 풀이 고갈되면 {@code CannotGetJdbcConnectionException}이
 * 트랜잭션 시작 단계에서 튀어나와 본문의 try-catch는 실행조차 되지 않는다. 즉 완전한 방어는
 * 프록시 <b>바깥</b>, 주입해 호출하는 쪽에만 놓을 수 있다.
 * <p>
 * 주입은 이 인터페이스 타입으로 받아야 한다. {@code <tx:annotation-driven>}에
 * {@code proxy-target-class}가 없어 JDK 동적 프록시가 만들어지므로, 구현체 타입으로 필드를
 * 선언하면 주입 자체가 실패한다.
 */
public interface SearchHistoryService {

    /**
     * 검색어를 기록한다. 이미 있는 키워드면 {@code searched_at}만 갱신한다.
     * <p>
     * 개수 제한이 없다 — 검색어는 삭제하지 않고 전부 보관한다. LOCATION-003의 "최대 5개"는 저장
     * 한도가 아니라 {@code GET /api/store/keywords}의 {@code recent}가 내려주는 화면 표시 개수다.
     * 저장 한도로 두면 오래된 인기 검색어가 다른 사용자의 6번째 검색에 밀려 삭제되면서
     * LOCATION-006 집계에서 누락된다.
     * <p>
     * {@code keyword}가 {@code null}이거나 공백뿐이면 아무것도 하지 않는다.
     * 앞뒤 공백을 다듬지 않고 받은 값을 그대로 저장한다 — 정규화는 호출부의 책임이다.
     *
     * @param userId  인증 컨텍스트에서 온 사용자 식별자
     * @param keyword 조회에 실제로 사용한 검색어
     * @throws org.springframework.dao.DataAccessException 기록 실패 시. 호출부가 반드시 잡아야 한다.
     */
    void record(Long userId, String keyword);
}
