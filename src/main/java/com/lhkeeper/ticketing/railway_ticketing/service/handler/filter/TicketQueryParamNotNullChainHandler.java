package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;

public class TicketQueryParamNotNullChainHandler implements TicketQueryChainFilter<TicketPageQueryReqDTO> {


    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {
        if (StringUtil.isBlank(requestParam.getStartRegionCode())) {
            throw new ClientException("出发地不能为空");
        }
        if (StringUtil.isBlank(requestParam.getEndRegionCode())) {
            throw new ClientException("目的地不能为空");
        }
        if (requestParam.getDepartureDate() == null) {
            throw new ClientException("出发日期不能为空");
        }

    }

    @Override
    public int getOrder() {
        return 0;
    }
}
