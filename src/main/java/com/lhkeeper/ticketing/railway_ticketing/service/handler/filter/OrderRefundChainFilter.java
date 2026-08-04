package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface OrderRefundChainFilter extends AbstractChainFilter<com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.ORDER_REFUND.name();
    }
}
