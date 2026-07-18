package com.fitwallet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitwallet.domain.HealthCheck;
import com.fitwallet.mapper.HealthMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HealthMapper healthMapper;

    @GetMapping("/")
    public String home() {
        return "fitwallet-backend is running";
    }

    @GetMapping("/health/db")
    public HealthCheck healthDb() {
        return healthMapper.findLatest();
    }
}
