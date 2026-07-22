package com.fitwallet.global.common.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.fitwallet.global.common.HealthCheck;

@Mapper
public interface HealthMapper {

    HealthCheck findLatest();
}
