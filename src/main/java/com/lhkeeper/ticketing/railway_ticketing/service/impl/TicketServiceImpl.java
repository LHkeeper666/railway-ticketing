package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatClassDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TrainServiceDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.*;
import java.util.stream.Collectors;

import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryContext;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        // 参数校验
        ticketPageQueryContext.handler(ChainMarkEnum.TICKET_QUERY.name(), ticketPageQueryReqDTO);

        List<TrainServiceDTO> trainServices = null;
        // 获取区域name
        List<String> buildRegionCodeToNameKeys = new ArrayList<>();
        buildRegionCodeToNameKeys.add(ticketPageQueryReqDTO.getStartRegionCode());
        buildRegionCodeToNameKeys.add(ticketPageQueryReqDTO.getEndRegionCode());
        buildRegionCodeToNameKeys =  buildRegionCodeToNameKeys.stream().map(each -> String.format(
                RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, each)).toList();

        List<String> regionNames = stringRedisTemplate.opsForValue().multiGet(buildRegionCodeToNameKeys);
        String startRegionName = regionNames.get(0);
        String endRegionName = regionNames.get(1);

        if (startRegionName == null || endRegionName == null) {
            List<Station> stations = stationMapper.selectList(Wrappers.lambdaQuery(Station.class));

            Map<String, String> regionCodeToNameMap = stations.stream().collect(Collectors.toMap(
                    station -> String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, station.getRegionCode()),
                    Station::getRegionName,
                    (existingValue, newValue) -> newValue
            ));
            stringRedisTemplate.opsForValue().multiSet(regionCodeToNameMap);
            regionNames = stringRedisTemplate.opsForValue().multiGet(buildRegionCodeToNameKeys);
            startRegionName = regionNames.get(0);
            endRegionName = regionNames.get(1);
        }

        // 根据区域name查询车次
        String buildTrainStationRelationHashKey = String.format(RedisConstant.TRAIN_STATION_RELATION_MAPPING, startRegionName, endRegionName);
        Map<Object, Object> trainStationRelationMap = stringRedisTemplate.opsForHash().entries(buildTrainStationRelationHashKey);
        List<TrainStationRelation> trainStationRelations = null;
        if (trainStationRelationMap.isEmpty()) {
            // 查数据库
            trainStationRelations = trainStationRelationMapper.selectList(
                    Wrappers.lambdaQuery(TrainStationRelation.class)
                            .eq(TrainStationRelation::getStartRegion, startRegionName)
                            .eq(TrainStationRelation::getEndRegion, endRegionName)
            );
            trainStationRelationMap = trainStationRelations.stream().collect(Collectors.toMap(
                    trainStationRelation -> String.format(RedisConstant.TRAINID_TO_STATION_RELATION_MAPPING, trainStationRelation.getTrainId()),
                    JSON::toJSONString
            ));
            stringRedisTemplate.opsForHash().putAll(buildTrainStationRelationHashKey, trainStationRelationMap);
        }
        trainStationRelations = (trainStationRelations == null)?
                trainStationRelationMap.values().stream().map(each -> JSON.parseObject(each.toString(), TrainStationRelation.class)).collect(Collectors.toList()):
                trainStationRelations;

        // 复制
        trainServices = trainStationRelations.stream().map(each -> TrainServiceDTO.builder()
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

        // 2. 构造 Redis 键
        List<String> buildTrainInfoHashKeys = trainIds.stream()
                .map(each -> String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, each))
                .collect(Collectors.toList());

        // 3. 一次性查询 Redis 中的数据
        List<String> trainJSONStrings = stringRedisTemplate.opsForValue().multiGet(buildTrainInfoHashKeys);

        // 4. 统计为 null 的数量
        long count = trainJSONStrings.stream().filter(Objects::isNull).count();

        // 5. 如果有部分数据为 null，从数据库中获取并缓存到 Redis
        Map<Long, Train> trainIdMap = null;
        if (count > 0) {
            // 从数据库中查询缺失的数据
            trainIdMap = trainMapper.selectByIds(trainIds).stream()
                    .collect(Collectors.toMap(Train::getId, each -> each));

            // 将从数据库中查询到的数据存入 Redis
            Map<String, String> redisData = trainIdMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, entry.getKey()), // 构造 Redis 键
                            entry -> JSON.toJSONString(entry.getValue()) // 将 Train 对象转换为 JSON 字符串
                    ));
            stringRedisTemplate.opsForValue().multiSet(redisData);
        }

        // 6. 如果 Redis 中没有找到数据，使用数据库的数据，或者使用 Redis 中的数据
        trainIdMap = (trainIdMap == null) ?
                trainJSONStrings.stream()
                        .filter(Objects::nonNull)
                        .map(each -> JSON.parseObject(each, Train.class))
                        .collect(Collectors.toMap(Train::getId, train -> train)) :
                trainIdMap;

        // 内存映射
        Map<Long, Train> finalTrainIdMap = trainIdMap;
        trainServices.forEach(each -> {
            each.setTrainNumber(finalTrainIdMap.get(each.getTrainId()).getTrainNumber());
            each.setSaleStatus(finalTrainIdMap.get(each.getTrainId()).getSaleStatus());
            each.setSaleTime(finalTrainIdMap.get(each.getTrainId()).getSaleTime());
            each.setTrainTags(Arrays.stream(finalTrainIdMap.get(each.getTrainId()).getTrainTag().split(",")).toList());
            each.setTrainBrand(finalTrainIdMap.get(each.getTrainId()).getTrainBrand());
        });

        // 获取 seat_class_list
        for (TrainServiceDTO each : trainServices) {
            String key = String.format(RedisConstant.TICKET_STOCKING_MAPPING, each.getTrainId(), each.getStartStation(), each.getEndStation());

            String cachedString = stringRedisTemplate.opsForValue().get(key);
            List<Seat> seats = null;
            if (cachedString == null) {
                seats = seatMapper.selectList(
                        Wrappers.lambdaQuery(Seat.class)
                                .eq(Seat::getTrainId, each.getTrainId())
                                .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                                .eq(Seat::getStartStation, each.getStartStation())
                                .eq(Seat::getEndStation, each.getEndStation())
                );
            } else {
                seats = JSON.parseArray(cachedString, Seat.class);
            }
            Map<Integer, List<Seat>> seatTypeToSeatsMap =
                    seats.stream()
                            .collect(Collectors.groupingBy(Seat::getSeatType));
            // 组装 seatClassList
            List<SeatClassDTO> seatClassDTOList = seatTypeToSeatsMap.entrySet()
                    .stream().map(entry -> SeatClassDTO.builder()
                            .type(entry.getKey())
                            .quantity(entry.getValue().size())
                            .price(entry.getValue().get(0).getPrice())
                            .build()
                    ).toList();
            each.setSeatClassList(seatClassDTOList);
        }

        return TicketPageQueryRespDTO.builder()
                .trainServiceList(trainServices)
                .build();
    }
}
