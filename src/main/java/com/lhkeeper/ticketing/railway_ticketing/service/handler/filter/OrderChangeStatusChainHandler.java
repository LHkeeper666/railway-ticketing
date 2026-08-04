package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangeReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 改签订单状态校验（order 1）
 */
@Component
@RequiredArgsConstructor
public class OrderChangeStatusChainHandler implements OrderChangeChainFilter {

    private final OrderMapper orderMapper;

    @Override
    public void handler(ChangeReqDTO reqDTO) {
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, reqDTO.getOrderSn())
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }
        if (!order.getUserId().equals(UserContext.get().getUserId())) {
            throw new ClientException("无权操作此订单");
        }
        if (!OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            throw new ClientException("只有已支付订单才能改签");
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
