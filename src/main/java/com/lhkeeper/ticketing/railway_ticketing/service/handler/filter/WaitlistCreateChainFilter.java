package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface WaitlistCreateChainFilter<T extends WaitlistCreateReqDTO> extends AbstractChainFilter<WaitlistCreateReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.WAITLIST_CREATE.name();
    }
}
