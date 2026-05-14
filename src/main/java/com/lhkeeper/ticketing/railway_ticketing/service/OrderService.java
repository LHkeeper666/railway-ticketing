package com.lhkeeper.ticketing.railway_ticketing.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.FlashOrderMessageDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.FlashOrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;

/**
 * 订单服务接口，提供订单创建、支付、取消、详情查询及抢票排队功能
 */
public interface OrderService extends IService<Order> {

    /** 创建普通购票订单，同步选座并锁定座位 */
    OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO);

    /** 抢票排队，订单写入 MQ 异步选座 */
    FlashOrderCreateRespDTO flashCreateOrder(OrderCreateReqDTO reqDTO);

    /** MQ 消费端处理抢票选座逻辑 */
    void processFlashOrder(FlashOrderMessageDTO msg);

    /** 模拟支付，生成支付记录 */
    void payOrder(String orderSn);

    /** 处理第三方支付回调通知 */
    void handlePayNotify(PayCallbackReqDTO reqDTO);

    /** 查询订单详情（含订单项和支付信息） */
    OrderDetailRespDTO getOrderDetail(String orderSn);

    /** 取消订单，恢复座位库存并失效缓存 */
    void cancelOrder(String orderSn);

    /** 取消订单，timeoutCancel=true 表示超时取消（仅允许 UNPAID 订单），false 为手动取消 */
    void cancelOrder(String orderSn, boolean timeoutCancel);
}
