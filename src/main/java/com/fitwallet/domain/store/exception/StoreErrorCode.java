package com.fitwallet.domain.store.exception;

import com.fitwallet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements ErrorCode {

    KEYWORD_OR_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "검색어 또는 카테고리를 입력해 주세요."),
    COORDINATE_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "위도와 경도를 함께 전달해 주세요."),
    INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "좌표 값이 올바르지 않습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."),
    RADIUS_EXCEEDED(HttpStatus.BAD_REQUEST, "주변 조회 반경은 최대 3km까지입니다."),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST, "검색 반경이 올바르지 않습니다."),
    LOCATION_AGREEMENT_REQUIRED(HttpStatus.FORBIDDEN, "위치 정보 이용에 동의해주세요."),
    SEARCH_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 검색 기록입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
