package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderListReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderListChainFilter<T extends OrderListReqDTO> extends AbstractChainFilter<OrderListReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_LIST.name();
    }
}
