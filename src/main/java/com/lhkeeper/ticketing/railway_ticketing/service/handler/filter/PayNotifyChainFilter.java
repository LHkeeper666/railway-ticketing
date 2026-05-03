package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface PayNotifyChainFilter<T extends PayCallbackReqDTO> extends AbstractChainFilter<PayCallbackReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.PAY_NOTIFY.name();
    }
}
