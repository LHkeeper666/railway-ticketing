package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface TicketQueryChainFilter<T extends TicketPageQueryReqDTO> extends AbstractChainFilter<TicketPageQueryReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.TICKET_QUERY.name();
    }
}
