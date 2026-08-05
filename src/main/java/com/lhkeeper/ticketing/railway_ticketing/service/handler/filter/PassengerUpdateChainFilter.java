package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface PassengerUpdateChainFilter<T extends PassengerUpdateReqDTO> extends AbstractChainFilter<PassengerUpdateReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.PASSENGER_UPDATE.name();
    }
}
