package com.lhkeeper.ticketing.railway_ticketing.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;

/**
 * 订单表 服务类
 */
public interface OrderService extends IService<Order> {

    public OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO);
}
