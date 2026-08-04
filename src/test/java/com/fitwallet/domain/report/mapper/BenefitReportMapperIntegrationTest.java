package com.fitwallet.domain.report.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(locations = "classpath:root-context.xml")
@Transactional
class BenefitReportMapperIntegrationTest {

    @Autowired
    private BenefitReportMapper benefitReportMapper;

    @Test
    void 시드_유저의_받은_혜택_총액을_조회한다() {
        BigDecimal result = benefitReportMapper.getTotalReceivedBenefit(1L, "2026-04");

        assertThat(result).isNotNull();
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void 시드_유저의_카테고리별_받은_혜택은_5개_이하로_조회된다() {
        var result = benefitReportMapper.getCategoryBenefits(1L, "2026-04");

        assertThat(result.size()).isLessThanOrEqualTo(5);
    }
}