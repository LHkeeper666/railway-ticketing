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
 * 订单表 服务类
 */
public interface OrderService extends IService<Order> {

    OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO);

    FlashOrderCreateRespDTO flashCreateOrder(OrderCreateReqDTO reqDTO);

    void processFlashOrder(FlashOrderMessageDTO msg);

    void payOrder(String orderSn);

    void handlePayNotify(PayCallbackReqDTO reqDTO);

    OrderDetailRespDTO getOrderDetail(String orderSn);

    void cancelOrder(String orderSn);
}
