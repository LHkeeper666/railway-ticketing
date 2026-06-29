package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 订单创建座位类型一致性校验（order 1）
 */
@Component
public class OrderCreateSeatTypeChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        if (requestParam == null || requestParam.getPassengers() == null || requestParam.getPassengers().isEmpty()) {
            return; // 前置校验已处理
        }

        // 校验同一订单只能选择同一种座位类型
        Integer firstSeatType = requestParam.getPassengers().get(0).getSeatType();
        boolean hasDifferentType = requestParam.getPassengers().stream()
                .anyMatch(p -> !Objects.equals(p.getSeatType(), firstSeatType));

        if (hasDifferentType) {
            throw new ClientException("同一订单只能选择同一种座位类型");
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
