package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 支付参数有效性校验：订单存在性、状态合法性（order 5）
 */
@Component
@RequiredArgsConstructor
public class OrderPayParamValidateChainHandler implements OrderPayChainFilter<String> {

    private final OrderMapper orderMapper;

    @Override
    public void handler(String orderSn) {
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }
        if (Objects.equals(order.getStatus(), OrderStatusEnum.PAID.getCode())) {
            throw new ClientException("订单已支付，不要重复支付");
        }
        if (Objects.equals(order.getStatus(), OrderStatusEnum.CANCELED.getCode())) {
            throw new ClientException("订单已取消，无法支付");
        }
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
