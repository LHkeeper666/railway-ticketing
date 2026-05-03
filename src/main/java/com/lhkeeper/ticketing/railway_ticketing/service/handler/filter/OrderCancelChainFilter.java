package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderCancelChainFilter<T extends String> extends AbstractChainFilter<String> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_CANCEL.name();
    }
}
