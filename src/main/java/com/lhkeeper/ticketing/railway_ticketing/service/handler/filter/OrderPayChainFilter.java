package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderPayChainFilter<T extends String> extends AbstractChainFilter<String> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_PAY.name();
    }
}
