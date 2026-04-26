package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.StationMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TicketQueryParamVerifyChainHandler implements TicketQueryChainFilter<TicketPageQueryReqDTO> {

    private final StationMapper stationMapper;

    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {
        boolean startRegionExists = stationMapper.exists(
                Wrappers.lambdaQuery(Station.class)
                        .eq(Station::getRegionCode, requestParam.getStartRegionCode())
        );
        boolean endRegionExists = stationMapper.exists(
                Wrappers.lambdaQuery(Station.class)
                        .eq(Station::getRegionCode, requestParam.getEndRegionCode())
        );
        if (!startRegionExists) {
            throw new ClientException("出发地不存在");
        }
        if (!endRegionExists) {
            throw new ClientException("目的地不存在");
        }
        if (Objects.equals(requestParam.getStartRegionCode(), requestParam.getEndRegionCode())) {
            throw new ClientException("出发地和目的地不能相同");
        }
        // 校验日期
//        if (!DateUtil.validateFormat(requestParam.getDepartureDate(), "yyyy-MM-dd")) {
//            throw new ClientException("出发日期格式错误，必须使用格式\"yyyy-MM-dd\"");
//        }
        if (DateUtil.beforeToday(requestParam.getDepartureDate())) {
            throw new ClientException("出发日期不能小于当前日期");
        }
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
