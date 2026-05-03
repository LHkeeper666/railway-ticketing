package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.LoginReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface AuthLoginChainFilter<T extends LoginReqDTO> extends AbstractChainFilter<LoginReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.AUTH_LOGIN.name();
    }
}
