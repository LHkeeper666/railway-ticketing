package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TODO: 库存校验
 */
@Component
@RequiredArgsConstructor
public class OrderCreateStockChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    private final SeatMapper seatMapper;

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        // TODO

    }

    @Override
    public int getOrder() {
        return 10;
    }
}
