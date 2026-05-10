package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatClassDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TrainServiceDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationPriceMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 余票查询服务实现，基于 Redis 缓存 + 分布式锁的查询逻辑
 */
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final RegionMapper regionMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationMapper trainStationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryContext;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 分页查询余票：缓存读取区域映射 → 查车次关系 → 查列车信息 → 查座位库存 → 组装返回
     */
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
            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "station_relation";
            boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
            if (!lockAcquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                stringRedisTemplate.opsForHash().putAll(buildTrainStationRelationHashKey, trainStationRelationMap);
                stringRedisTemplate.expire(buildTrainStationRelationHashKey, RedisConstant.CACHE_TTL_STATION_RELATION, TimeUnit.SECONDS);
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
        List<Long> cachedTrainIds = new ArrayList<>();
        List<Long> trainIdList = new ArrayList<>(trainIds);
        for (int i = 0; i < trainJSONStrings.size(); i++) {
            String json = trainJSONStrings.get(i);
            if (json != null) {
                Long trainId = trainIdList.get(i);
                cachedTrainIds.add(trainId);
                if (!RedisConstant.NULL_PLACEHOLDER.equals(json)) {
                    Train train = JSON.parseObject(json, Train.class);
                    trainIdMap.put(train.getId(), train);
                }
            }
        }

        // 只查询缺失的 trainId 数据
        Set<Long> missingTrainIds = new HashSet<>(trainIds);
        cachedTrainIds.forEach(missingTrainIds::remove);
        if (!missingTrainIds.isEmpty()) {
            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "train_batch";
            boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
            if (!lockAcquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                List<Train> missingTrains = trainMapper.selectByIds(missingTrainIds);
                Set<Long> foundTrainIds = new HashSet<>();
                missingTrains.forEach(train -> {
                    trainIdMap.put(train.getId(), train);
                    foundTrainIds.add(train.getId());
                    stringRedisTemplate.opsForValue().set(
                            String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, train.getId()),
                            JSON.toJSONString(train),
                            RedisConstant.CACHE_TTL_TRAIN_INFO + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_TRAIN_INFO / 10),
                            TimeUnit.SECONDS);
                });
                // 缓存空值防止穿透
                missingTrainIds.stream()
                        .filter(id -> !foundTrainIds.contains(id))
                        .forEach(id -> stringRedisTemplate.opsForValue().set(
                                String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, id),
                                RedisConstant.NULL_PLACEHOLDER,
                                RedisConstant.CACHE_TTL_NULL,
                                TimeUnit.SECONDS));
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
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

        // 预加载所有列车的站点序列，用于计算位图掩码
        Set<Long> trainIdsForStations = trainServices.stream().map(TrainServiceDTO::getTrainId).collect(Collectors.toSet());
        Map<Long, List<TrainStation>> trainStationMap = new HashMap<>();
        for (Long tid : trainIdsForStations) {
            List<TrainStation> stations = trainStationMapper.selectList(
                    Wrappers.lambdaQuery(TrainStation.class)
                            .eq(TrainStation::getTrainId, tid)
            );
            trainStationMap.put(tid, stations);
        }

        // 获取 seat_class_list
        for (TrainServiceDTO each : trainServices) {
            String key = String.format(RedisConstant.TICKET_STOCKING_MAPPING, each.getTrainId(), each.getStartStation(), each.getEndStation());

            String cachedString = stringRedisTemplate.opsForValue().get(key);
            List<Seat> seats = null;
            if (cachedString == null) {
                long queryMask = StationCalculateUtil.bitmapMask(
                        trainStationMap.get(each.getTrainId()), each.getStartStation(), each.getEndStation());
                seats = seatMapper.selectList(
                        Wrappers.lambdaQuery(Seat.class)
                                .eq(Seat::getTrainId, each.getTrainId())
                                .apply("(seat_bitmap & {0}) = 0", queryMask)
                );
                // 为各座位类型设置正确的区间价格
                setSeatPrices(seats, each.getTrainId(), each.getStartStation(), each.getEndStation());

                String lockKey = RedisConstant.LOCK_KEY_PREFIX + "seat:" + each.getTrainId() + ":" + each.getStartStation() + ":" + each.getEndStation();
                boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
                if (!lockAcquired) {
                    throw new ServiceException("系统正忙，请稍后重试");
                }
                try {
                    stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(seats),
                            RedisConstant.CACHE_TTL_SEAT_STOCK + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_SEAT_STOCK / 10),
                            TimeUnit.SECONDS);
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

    private void setSeatPrices(List<Seat> seats, Long trainId, String startStation, String endStation) {
        if (seats.isEmpty()) return;
        Set<Integer> seatTypes = seats.stream().map(Seat::getSeatType).collect(Collectors.toSet());
        List<TrainStationPrice> prices = trainStationPriceMapper.selectList(
                Wrappers.lambdaQuery(TrainStationPrice.class)
                        .eq(TrainStationPrice::getTrainId, trainId)
                        .eq(TrainStationPrice::getStartStation, startStation)
                        .eq(TrainStationPrice::getEndStation, endStation)
                        .in(TrainStationPrice::getSeatType, seatTypes)
        );
        Map<Integer, BigDecimal> priceMap = prices.stream()
                .collect(Collectors.toMap(
                        TrainStationPrice::getSeatType,
                        p -> BigDecimal.valueOf(p.getPrice())
                ));
        seats.forEach(seat -> {
            BigDecimal p = priceMap.get(seat.getSeatType());
            if (p != null) seat.setPrice(p);
        });
    }
}
