package com.fitwallet.domain.report.mapper;

import com.fitwallet.domain.report.dto.response.MissedSummaryResponse;
import com.fitwallet.domain.report.dto.response.MissedTransactionRawResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MissedBenefitMapper {

    /** 앱 미사용 손실 / 카드 선택 손실 총액(두 손실 모두 "더 좋은 카드가 있었던 건"만 집계). */
    MissedSummaryResponse getMissedSummary(@Param("userId") Long userId,
                                           @Param("yearMonth") String yearMonth);

    /** 선택한 손실 종류(is_used_app)의 놓친 거래 상세. 카테고리 그룹핑은 서비스에서 한다. */
    List<MissedTransactionRawResponse> getMissedTransactions(@Param("userId") Long userId,
                                                             @Param("yearMonth") String yearMonth,
                                                             @Param("isUsedApp") int isUsedApp);
}
