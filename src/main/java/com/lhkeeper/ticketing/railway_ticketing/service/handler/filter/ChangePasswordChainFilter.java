package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangePasswordReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface ChangePasswordChainFilter<T extends ChangePasswordReqDTO> extends AbstractChainFilter<ChangePasswordReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.CHANGE_PASSWORD.name();
    }
}
