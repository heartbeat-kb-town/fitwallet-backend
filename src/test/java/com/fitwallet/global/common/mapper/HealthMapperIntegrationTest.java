package com.fitwallet.global.common.mapper;

import com.fitwallet.global.common.dto.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper 통합 테스트. docker compose로 띄운 실제 MySQL과 시드 데이터를 사용한다.
 */
@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class HealthMapperIntegrationTest {

    @Autowired
    private HealthMapper healthMapper;

    @Test
    void findLatest_실제_DB에_연결해서_시드_데이터를_조회한다() {
        HealthCheckResponse healthCheck = healthMapper.findLatest();

        assertThat(healthCheck).isNotNull();
        assertThat(healthCheck.getMessage()).isEqualTo("fitwallet-backend DB connection OK");
        // 수동 별칭 없이 checked_at → checkedAt 이 매핑되는지 (mapUnderscoreToCamelCase)
        assertThat(healthCheck.getCheckedAt()).isNotNull();
    }
}
