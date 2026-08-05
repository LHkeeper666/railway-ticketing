package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface PassengerDeleteChainFilter extends AbstractChainFilter<Long> {

    @Override
    default String mark() {
        return ChainMarkEnum.PASSENGER_DELETE.name();
    }
}
