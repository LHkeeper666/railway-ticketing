package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface AuthRegisterChainFilter<T extends RegisterReqDTO> extends AbstractChainFilter<RegisterReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.AUTH_REGISTER.name();
    }
}
