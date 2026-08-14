package com.fitwallet.global.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애플리케이션이 뜰 때 DB 스키마 마이그레이션을 적용한다.
 *
 * <p>배선은 {@code root-context.xml}에 있고, {@code sqlSessionFactory}가 {@code depends-on}으로
 * 이 빈 뒤에 뜬다. 마이그레이션이 실패하면 컨텍스트 기동이 실패하므로, 스키마가 맞지 않는 코드가
 * 서비스되는 상황이 생기지 않는다.
 *
 * <p>CI에서 돌리지 않고 앱 기동 시점에 돌리는 이유는 접근 경로 때문이다. 운영 RDS는 보안그룹이
 * 3306을 EB 보안그룹과 지정 IP에만 여는데, GitHub Actions 러너는 IP가 매번 바뀌어 붙일 수 없다.
 * 앱은 이미 접속 권한을 갖고 있고, EB가 단일 인스턴스라 동시 실행 경합도 없다.
 *
 * <p>Flyway 설정 객체는 fluent 빌더({@code dataSource(...)})라 XML 프로퍼티 주입이 되지 않는다.
 * 자바 {@code @Configuration}을 하나 더 만드는 대신(§5의 {@code SwaggerConfig} 예외를 늘리지
 * 않는다) JavaBean setter를 가진 이 얇은 빈을 두고, 배선과 값은 XML·properties에 남긴다.
 */
public class FlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrator.class);

    private DataSource dataSource;

    /**
     * 콤마로 구분한 Flyway location 목록. 프로파일마다 다르다.
     * 로컬·CI는 {@code db/migration,db/seed-local}, 운영은 {@code db/migration}만 쓴다 —
     * 스키마와 참조 데이터는 모든 환경이 같고 데모 데이터만 환경별로 다르다.
     */
    private String locations;

    /**
     * 이미 데이터가 들어 있는데 이력 테이블이 없는 DB를 "여기까지는 적용됨"으로 표시할 버전.
     * 빈 DB에는 적용되지 않는다(그때는 baseline을 찍지 않고 V1부터 전부 실행한다).
     *
     * <p>운영 RDS는 2026-08-05 배포 시점 상태로 굳어 있어 V2(참조 데이터)까지 가진 것으로 본다.
     * V3 이후는 전부 멱등하게 작성하므로, 이 값이 실제보다 낮아 다시 실행돼도 안전하다.
     */
    private String baselineVersion;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    /** {@code root-context.xml}의 {@code init-method}. */
    public void migrate() {
        String[] resolved = locations.split("\\s*,\\s*");
        log.info("Flyway 마이그레이션 시작 — locations={}, baselineVersion={}",
                String.join(", ", resolved), baselineVersion);

        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations(resolved)
                // 데모 시드(db/seed-local)가 V900번대를 쓰기 때문에 반드시 필요하다.
                //
                // 로컬·CI는 db/migration과 db/seed-local을 같은 이력 테이블에 함께 적용한다.
                // 시드를 적용하고 나면 이력의 최고 버전이 901이 되는데, 그 뒤에 스키마 변경으로
                // V7을 추가하면 Flyway는 "이미 901까지 적용됐는데 7이 미적용"으로 보고
                // out-of-order 판정해 validate 단계에서 기동을 막는다
                // (Detected resolved migration not applied to database: 7).
                //
                // 시드 번호를 900번대로 띄운 것은 스키마보다 뒤에 적용되게 하려는 의도적 설계이므로,
                // 낮은 번호가 나중에 들어오는 것은 이 저장소에서 정상 상황이다. 이 플래그가 없으면
                // 앞으로 추가되는 모든 db/migration 버전이 로컬에서 동일하게 막힌다.
                //
                // 운영은 locations에 seed-local이 없어 이력 최고 버전이 항상 스키마 쪽이라
                // 애초에 out-of-order가 생기지 않는다. 적용 순서는 이 플래그와 무관하게
                // 버전 오름차순으로 유지된다 — 허용되는 것은 "뒤늦은 합류"뿐이다.
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .baselineVersion(baselineVersion)
                .load()
                .migrate();

        log.info("Flyway 마이그레이션 완료 — {}건 적용, 현재 버전 {}",
                result.migrationsExecuted, result.targetSchemaVersion);
    }
}
