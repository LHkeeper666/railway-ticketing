package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付结果回调入参（模拟第三方支付平台回调）
 */
@Data
public class PayCallbackReqDTO {

    /**
     * 商户订单号
     */
    private String orderSn;

    /**
     * 第三方交易流水号
     */
    private String tradeNo;

    /**
     * 支付渠道（ALIPAY / WECHAT）
     */
    private String channel;

    /**
     * 交易金额
     */
    private BigDecimal totalAmount;

    /**
     * 支付状态（SUCCESS / FAIL）
     */
    private String status;

    /**
     * 支付时间（yyyy-MM-dd HH:mm:ss）
     */
    private String gmtPayment;

    /**
     * 签名
     */
    private String sign;
}
