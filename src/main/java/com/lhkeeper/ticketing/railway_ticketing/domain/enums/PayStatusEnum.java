package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付状态枚举
 */
@Getter
@RequiredArgsConstructor
public enum PayStatusEnum {

    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAIL("FAIL"),
    FROZEN("FROZEN"),
    PENDING_REFUND("PENDING_REFUND"),
    REFUNDED("REFUNDED");

    private final String code;
}
