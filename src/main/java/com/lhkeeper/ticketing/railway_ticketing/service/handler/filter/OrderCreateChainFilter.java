package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderCreateChainFilter<T extends OrderCreateReqDTO> extends AbstractChainHandler<OrderCreateReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_CREATE.name();
    }
}
