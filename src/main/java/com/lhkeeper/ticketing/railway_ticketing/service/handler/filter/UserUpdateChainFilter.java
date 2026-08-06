package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.UserUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface UserUpdateChainFilter<T extends UserUpdateReqDTO> extends AbstractChainFilter<UserUpdateReqDTO> {

    @Override
    default String mark() {
        return ChainMarkEnum.USER_UPDATE.name();
    }
}
