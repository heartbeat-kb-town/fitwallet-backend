package com.fitwallet.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.fitwallet.domain.HealthCheck;

@SpringJUnitConfig(locations = "file:src/main/webapp/WEB-INF/spring/root-context.xml")
class HealthMapperIntegrationTest {

    @Autowired
    private HealthMapper healthMapper;

    @Test
    void findLatest_실제_DB에_연결해서_시드_데이터를_조회한다() {
        HealthCheck healthCheck = healthMapper.findLatest();

        assertNotNull(healthCheck);
        assertEquals("fitwallet-backend DB connection OK", healthCheck.getMessage());
    }
}
