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
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import lombok.RequiredArgsConstructor;
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

    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final RegionMapper regionMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryContext;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:ticket:";

    @Override
    public TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        // 参数校验
        ticketPageQueryContext.handler(ChainMarkEnum.TICKET_QUERY.name(), ticketPageQueryReqDTO);

        List<TrainServiceDTO> trainServices = null;
        // 获取区域name
        List<String> buildRegionCodeToNameKeys = Arrays.asList(
                String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, ticketPageQueryReqDTO.getStartRegionCode()),
                String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, ticketPageQueryReqDTO.getEndRegionCode())
        );

        List<String> regionNames = stringRedisTemplate.opsForValue().multiGet(buildRegionCodeToNameKeys);

        // 在抽象责任链中已经保证不为null了
//        if (regionNames == null || regionNames.contains(null)) {
//            List<Region> regions = regionMapper.selectList(Wrappers.emptyWrapper());
//            Map<String, String> codeToNameMap = regions.stream().collect(Collectors.toMap(
//                    region -> String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, region.getCode()),
//                    Region::getName));
//            stringRedisTemplate.opsForValue().multiSet(codeToNameMap);
//            regionNames = stringRedisTemplate.opsForValue().multiGet(buildRegionCodeToNameKeys);
//        }
        String startRegionName = regionNames.get(0);
        String endRegionName = regionNames.get(1);

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
            String lockKey = LOCK_KEY_PREFIX + "station_relation";
            boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 10, TimeUnit.SECONDS));
            if (!lockAcquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                stringRedisTemplate.opsForHash().putAll(buildTrainStationRelationHashKey, trainStationRelationMap);
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
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
        Set<Long> trainIds = trainServices.stream().map(TrainServiceDTO::getTrainId).collect(Collectors.toSet());

        // 2. 构造 Redis 键
        List<String> buildTrainInfoHashKeys = trainIds.stream()
                .map(each -> String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, each))
                .collect(Collectors.toList());

        List<String> trainJSONStrings = stringRedisTemplate.opsForValue().multiGet(buildTrainInfoHashKeys);
        Map<Long, Train> trainIdMap = new HashMap<>();

        // 过滤出已存在的 Redis 数据
        List<Long> existingTrainIds = new ArrayList<>();
        for (int i = 0; i < trainJSONStrings.size(); i++) {
            if (trainJSONStrings.get(i) != null) {
                Train train = JSON.parseObject(trainJSONStrings.get(i), Train.class);
                trainIdMap.put(train.getId(), train);
                existingTrainIds.add(train.getId());
            }
        }

        // 只查询缺失的 trainId 数据
        // TODO: 加锁
        Set<Long> missingTrainIds = new HashSet<>(trainIds);
        existingTrainIds.forEach(missingTrainIds::remove);
        if (!missingTrainIds.isEmpty()) {
            List<Train> missingTrains = trainMapper.selectByIds(missingTrainIds);
            missingTrains.forEach(train -> {
                trainIdMap.put(train.getId(), train);
                // 将缺失的 train 数据放入 Redis
                stringRedisTemplate.opsForValue().set(String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, train.getId()), JSON.toJSONString(train));
            });
        }

        // 内存映射
        trainServices.forEach(each -> {
            Train train = trainIdMap.get(each.getTrainId());
            each.setTrainNumber(train.getTrainNumber());
            each.setSaleStatus(train.getSaleStatus());
            each.setSaleTime(train.getSaleTime());
            each.setTrainTags(Arrays.stream(train.getTrainTag().split(",")).toList());
            each.setTrainBrand(train.getTrainBrand());
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
                String lockKey = LOCK_KEY_PREFIX + "seat:" + each.getTrainId() + ":" + each.getStartStation() + ":" + each.getEndStation();
                boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 10, TimeUnit.SECONDS));
                if (!lockAcquired) {
                    throw new ServiceException("系统正忙，请稍后重试");
                }
                try {
                    stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(seats));
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
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
