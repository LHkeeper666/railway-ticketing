package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

public enum OrderStatusEnum {

    UNPAID(0),
    PAID(1),
    CANCELED(2);

    private final Integer code;

    OrderStatusEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return this.code;
    }
}
