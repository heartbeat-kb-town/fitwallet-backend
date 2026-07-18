package com.fitwallet.mapper;

import com.fitwallet.domain.HealthCheck;

public interface HealthMapper {

    HealthCheck findLatest();
}
