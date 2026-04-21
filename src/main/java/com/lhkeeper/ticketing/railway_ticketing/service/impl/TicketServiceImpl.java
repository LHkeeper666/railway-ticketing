package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationRelation;
import com.lhkeeper.ticketing.railway_ticketing.mapper.StationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationRelationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * <p>
 * 车票表 服务实现类
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    private final TicketMapper ticketMapper;
    private final StationMapper stationMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;

    public TicketServiceImpl(TicketMapper ticketMapper, StationMapper stationMapper, TrainStationRelationMapper trainStationRelationMapper) {
        super();
        this.ticketMapper = ticketMapper;
        this.stationMapper = stationMapper;
        this.trainStationRelationMapper = trainStationRelationMapper;
    }

    @Override
    public TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        // 获取区域
        LambdaQueryWrapper<Station> queryWrapper = Wrappers.lambdaQuery(Station.class)
                .eq(Station::getStationCode, ticketPageQueryReqDTO.getStartRegionCode());
        Station fromStation = stationMapper.selectOne(queryWrapper);
        LambdaQueryWrapper<Station> queryWrapper2 = Wrappers.lambdaQuery(Station.class)
                .eq(Station::getStationCode, ticketPageQueryReqDTO.getEndRegionCode());
        Station toStation = stationMapper.selectOne(queryWrapper2);

        // 查询车次
        LambdaQueryWrapper<TrainStationRelation> queryWrapper3 = Wrappers.lambdaQuery(TrainStationRelation.class)
                .eq(TrainStationRelation::getStartRegion, fromStation.getRegionName())
                .eq(TrainStationRelation::getEndRegion, toStation.getRegionName());
        List<TrainStationRelation> trainStationRelation = trainStationRelationMapper.selectList(queryWrapper3);

        return new TicketPageQueryRespDTO().builder()
                .trainList(trainStationRelation)
                .build();
    }
}
