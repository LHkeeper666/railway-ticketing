package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatClassDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationPrice;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationPriceMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单创建库存校验，检查余票是否充足（order 10）
 */
@Component
@RequiredArgsConstructor
public class OrderCreateStockChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    private final SeatMapper seatMapper;
    private final TrainStationService trainStationService;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        Map<Integer, Long> requiredByType = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(
                        OrderCreatePassengerDetailDTO::getSeatType,
                        Collectors.counting()
                ));

        Long trainId = Long.valueOf(requestParam.getTrainId());
        String startStation = requestParam.getStartStation();
        String endStation = requestParam.getEndStation();

        String cacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING, trainId, startStation, endStation);
        String cachedJSON = stringRedisTemplate.opsForValue().get(cacheKey);
        List<SeatClassDTO> seatClassList;
        if (cachedJSON != null) {
            seatClassList = JSON.parseArray(cachedJSON, SeatClassDTO.class);
        } else {
            DistributedLock lock = lockFactory.tryLock(
                    "stock:" + trainId + ":" + startStation + ":" + endStation,
                    RedisConstant.LOCK_TTL_SECONDS);
            if (lock == null) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                cachedJSON = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cachedJSON != null) {
                    seatClassList = JSON.parseArray(cachedJSON, SeatClassDTO.class);
                } else {
                    List<TrainStation> stations = trainStationService.getTrainStationsByTrainId(trainId);
                    long queryMask = StationCalculateUtil.bitmapMask(stations, startStation, endStation);
                    List<Seat> seats = seatMapper.selectList(
                            Wrappers.lambdaQuery(Seat.class)
                                    .eq(Seat::getTrainId, trainId)
                                    .apply("(seat_bitmap & {0}) = 0", queryMask)
                    );
                    seatClassList = seats.stream()
                            .collect(Collectors.groupingBy(Seat::getSeatType))
                            .entrySet().stream().map(entry -> {
                                BigDecimal price = getPrice(trainId, startStation, endStation, entry.getKey());
                                return SeatClassDTO.builder()
                                        .type(entry.getKey())
                                        .quantity(entry.getValue().size())
                                        .price(price)
                                        .build();
                            }).toList();
                    stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(seatClassList),
                            RedisConstant.CACHE_TTL_SEAT_STOCK + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_SEAT_STOCK / 10),
                            TimeUnit.SECONDS);
                }
            } finally {
                lock.unlock();
            }
        }

        requiredByType.forEach((seatType, required) -> {
            long available = seatClassList.stream()
                    .filter(dto -> dto.getType().equals(seatType))
                    .mapToLong(SeatClassDTO::getQuantity)
                    .findFirst()
                    .orElse(0);
            if (available < required) {
                throw new ClientException("余票不足");
            }
        });
    }

    private BigDecimal getPrice(Long trainId, String startStation, String endStation, Integer seatType) {
        TrainStationPrice priceRecord = trainStationPriceMapper.selectOne(
                Wrappers.lambdaQuery(TrainStationPrice.class)
                        .eq(TrainStationPrice::getTrainId, trainId)
                        .eq(TrainStationPrice::getStartStation, startStation)
                        .eq(TrainStationPrice::getEndStation, endStation)
                        .eq(TrainStationPrice::getSeatType, seatType)
        );
        return priceRecord != null ? BigDecimal.valueOf(priceRecord.getPrice()) : BigDecimal.ZERO;
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
