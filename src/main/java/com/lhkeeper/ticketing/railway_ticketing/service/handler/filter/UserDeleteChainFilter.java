package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;

public interface UserDeleteChainFilter extends AbstractChainFilter<String> {

    @Override
    default String mark() {
        return ChainMarkEnum.USER_DELETE.name();
    }
}
