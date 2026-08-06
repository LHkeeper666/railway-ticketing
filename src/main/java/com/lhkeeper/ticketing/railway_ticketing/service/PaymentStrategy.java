package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.PayCreateRequest;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayCreateResult;

/**
 * 支付策略接口，定义支付渠道的抽象操作。
 * 每个支付渠道（Mock、Alipay、Wechat）需实现此接口并注册为 Spring Bean。
 */
public interface PaymentStrategy {

    /**
     * 支付渠道标识
     */
    String getChannel();

    /**
     * 创建支付，生成支付记录并返回支付链接/二维码等信息
     */
    PayCreateResult createPayment(PayCreateRequest request);

    /**
     * 回调验签
     */
    boolean verifySignature(PayCallbackReqDTO callback);

    /**
     * 主动查询支付状态
     */
    String queryStatus(String orderSn);

    /**
     * 退款
     */
    boolean refund(String orderSn, Integer amount);
}
