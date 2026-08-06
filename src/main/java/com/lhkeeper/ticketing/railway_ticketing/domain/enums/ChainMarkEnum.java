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
    WAITLIST_CREATE,

    /**
     * 退票过滤器
     */
    ORDER_REFUND,

    /**
     * 改签过滤器
     */
    ORDER_CHANGE,

    /**
     * 乘客创建过滤器
     */
    PASSENGER_CREATE,

    /**
     * 乘客更新过滤器
     */
    PASSENGER_UPDATE,

    /**
     * 乘客删除过滤器
     */
    PASSENGER_DELETE,

    USER_UPDATE,

    CHANGE_PASSWORD,

    USER_DELETE,

    ORDER_LIST;
}
