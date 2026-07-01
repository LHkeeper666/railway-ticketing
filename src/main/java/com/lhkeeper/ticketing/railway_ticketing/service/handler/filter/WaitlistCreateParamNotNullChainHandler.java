package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class WaitlistCreateParamNotNullChainHandler implements WaitlistCreateChainFilter<WaitlistCreateReqDTO> {

    @Override
    public void handler(WaitlistCreateReqDTO req) {
        if (StringUtil.isBlank(req.getTrainId())) {
            throw new ClientException("车次ID不能为空");
        }
        if (StringUtil.isBlank(req.getStartStation())) {
            throw new ClientException("出发站不能为空");
        }
        if (StringUtil.isBlank(req.getEndStation())) {
            throw new ClientException("到达站不能为空");
        }
        if (req.getSeatType() == null) {
            throw new ClientException("座位类型不能为空");
        }
        if (CollectionUtils.isEmpty(req.getPassengers())) {
            throw new ClientException("乘车人不能为空");
        }
        for (var p : req.getPassengers()) {
            if (StringUtil.isBlank(p.getPassengerId())) {
                throw new ClientException("乘车人ID不能为空");
            }
            if (p.getSeatType() == null) {
                throw new ClientException("乘车人座位类型不能为空");
            }
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
