package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface PassengerCreateChainFilter<T extends PassengerCreateReqDTO> extends AbstractChainFilter<PassengerCreateReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.PASSENGER_CREATE.name();
    }
}
