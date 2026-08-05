package com.fitwallet.global.common.mapper;

import com.fitwallet.global.common.dto.HealthCheckResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthMapper {

    HealthCheckResponse findLatest();
}
