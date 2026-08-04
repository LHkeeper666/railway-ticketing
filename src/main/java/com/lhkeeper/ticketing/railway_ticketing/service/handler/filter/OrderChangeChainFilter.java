package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangeReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderChangeChainFilter extends AbstractChainFilter<ChangeReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_CHANGE.name();
    }
}
