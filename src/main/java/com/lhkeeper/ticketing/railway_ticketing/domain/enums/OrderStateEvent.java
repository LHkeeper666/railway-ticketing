package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

/**
 * 订单状态转移触发事件
 */
public enum OrderStateEvent {

    /** 抢票成功 */
    FLASH_SUCCEED,
    /** 抢票失败 */
    FLASH_FAIL,
    /** 支付回调成功 */
    PAY_NOTIFY,
    /** 手动取消 */
    CANCEL,
    /** 超时自动取消 */
    TIMEOUT_CANCEL,
    /** 退票全部退完 */
    REFUND_ALL,
    /** 候补匹配成功 */
    WAITLIST_MATCH
}
