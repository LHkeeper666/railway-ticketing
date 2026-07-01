package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

public enum ChainMarkEnum {

    /**
     * 订单创建过滤器
     */
    ORDER_CREATE,

    /**
     * 车票查询过滤器
     */
    TICKET_QUERY,

    /**
     * 订单支付过滤器
     */
    ORDER_PAY,

    /**
     * 登录过滤器
     */
    AUTH_LOGIN,

    /**
     * 注册过滤器
     */
    AUTH_REGISTER,

    /**
     * 支付结果回调过滤器
     */
    PAY_NOTIFY,

    /**
     * 取消订单过滤器
     */
    ORDER_CANCEL,

    /**
     * 候补创建过滤器
     */
    WAITLIST_CREATE;
}
