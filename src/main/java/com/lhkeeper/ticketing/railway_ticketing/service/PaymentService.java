package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.PayCreateRequest;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayCreateResult;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayInfoDTO;

/**
 * 支付服务门面，负责渠道路由、Pay 表操作、回调状态更新。
 * 从 OrderService 剥离，保持支付逻辑独立。
 */
public interface PaymentService {

    /**
     * 创建支付，按 channel 路由策略
     */
    PayCreateResult createPayment(PayCreateRequest reqDTO);

    /**
     * 处理支付回调（验签 + CAS 状态更新）
     */
    void handleCallback(PayCallbackReqDTO reqDTO);

    /**
     * 查询支付信息
     */
    PayInfoDTO queryPayment(String orderSn);

    /**
     * 退款
     */
    void refund(String orderSn, Integer amount);
}
