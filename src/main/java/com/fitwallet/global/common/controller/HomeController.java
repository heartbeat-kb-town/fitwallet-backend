package com.fitwallet.global.common.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitwallet.global.common.dto.HealthCheckResponse;
import com.fitwallet.global.common.mapper.HealthMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HealthMapper healthMapper;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String home() {
        String dbStatus;
        try {
            HealthCheckResponse healthCheck = healthMapper.findLatest();
            dbStatus = "DB 연결 OK — " + healthCheck.getMessage() + " (" + healthCheck.getCheckedAt() + ")";
        } catch (Exception e) {
            dbStatus = "DB 연결 안 됨 — `docker compose up -d`로 로컬 MySQL을 먼저 띄워주세요.";
        }

        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <title>fitwallet-backend</title>
                </head>
                <body style="font-family: sans-serif; text-align: center; margin-top: 40px;">
                    <h1>fitwallet-backend is running</h1>
                    <p>%s</p>
                    <img src="/images/setup-success.jpg" alt="환경 구축 성공" style="max-width: 800px; width: 100%%;">
                </body>
                </html>
                """.formatted(dbStatus);
    }

    @GetMapping("/health/db")
    public HealthCheckResponse healthDb() {
        return healthMapper.findLatest();
    }
}
