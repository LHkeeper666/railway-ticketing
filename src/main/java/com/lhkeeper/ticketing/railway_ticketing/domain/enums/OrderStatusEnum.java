package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

/**
 * 订单状态枚举
 */
public enum OrderStatusEnum {

    /** 未支付 */
    UNPAID(0),
    /** 已支付 */
    PAID(1),
    /** 已取消 */
    CANCELED(2),
    /** 排队中（抢票） */
    PENDING(3);

    private final Integer code;

    OrderStatusEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return this.code;
    }
}
