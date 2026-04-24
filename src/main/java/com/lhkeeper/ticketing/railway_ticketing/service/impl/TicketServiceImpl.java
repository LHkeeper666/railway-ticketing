package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatClassDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TrainServiceDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    private final TicketMapper ticketMapper;
    private final StationMapper stationMapper;
    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;

    @Override
    public TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        List<TrainServiceDTO> trainServices = null;
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

        // 复制
        trainServices = trainStationRelation.stream().map(each -> TrainServiceDTO.builder()
                .arrivalFlag(each.getArrivalFlag())
                .departureFlag(each.getDepartureFlag())
                .endStation(each.getEndStation())
                .startStation(each.getStartStation())
                .trainId(each.getTrainId())
                .arrivalTime(each.getArrivalTime())
                .departureTime(each.getDepartureTime())
                .startRegion(each.getStartRegion())
                .endRegion(each.getEndRegion())
                .build()).toList();

        // 获取 train_number
        // 1. 收集所有 trainId
        Set<Long> trainIds = trainServices.stream()
                .map(TrainServiceDTO::getTrainId)
                .collect(Collectors.toSet());

        // 2. 一次性查询
        Map<Long, Train> trainIdMap =
                trainMapper.selectBatchIds(trainIds).stream()
                        .collect(Collectors.toMap(
                                Train::getId,
                                train -> train
                        ));

        // 3. 纯内存映射（无 IO、无副作用）
        trainServices.forEach(each -> {
            each.setTrainNumber(trainIdMap.get(each.getTrainId()).getTrainNumber());
            each.setSaleStatus(trainIdMap.get(each.getTrainId()).getSaleStatus());
            each.setSaleTime(trainIdMap.get(each.getTrainId()).getSaleTime());
            each.setTrainTags(Arrays.stream(trainIdMap.get(each.getTrainId()).getTrainTag().split(",")).toList());
            each.setTrainBrand(trainIdMap.get(each.getTrainId()).getTrainBrand());
        });

        // 获取 seat_class_list
        List<Seat> allSeats = seatMapper.selectList(
                Wrappers.lambdaQuery(Seat.class)
                        .in(Seat::getTrainId, trainIds)
                        .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
        );

        Map<String, List<Seat>> seatGroupMap =
                allSeats.stream().collect(Collectors.groupingBy(seat ->
                        seat.getTrainId() + "_" +
                        seat.getStartStation() + "_" +
                        seat.getEndStation()
                ));

        for (TrainServiceDTO each : trainServices) {

            String key = each.getTrainId() + "_" +
                    each.getStartStation() + "_" +
                    each.getEndStation();

            List<Seat> seats = seatGroupMap.getOrDefault(key, List.of());

            Map<Integer, List<Seat>> seatTypeToSeatsMap =
                    seats.stream()
                            .collect(Collectors.groupingBy(Seat::getSeatType));

            // 组装 seatClassList
            List<SeatClassDTO> seatClassDTOList = seatTypeToSeatsMap.entrySet()
                    .stream().map(entry -> SeatClassDTO.builder()
                            .type(entry.getKey())
                            .quantity(entry.getValue().size())
                            .price(BigDecimal.valueOf(entry.getValue().get(0).getPrice()))
                            .build()
                    ).toList();

            each.setSeatClassList(seatClassDTOList);
        }

        return TicketPageQueryRespDTO.builder()
                .trainServiceList(trainServices)
                .build();
    }
}
