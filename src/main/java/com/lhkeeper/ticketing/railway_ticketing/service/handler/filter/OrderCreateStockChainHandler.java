package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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
    private final TrainStationMapper trainStationMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
        List<Seat> availableSeats;
        if (cachedJSON != null) {
            availableSeats = JSON.parseArray(cachedJSON, Seat.class);
        } else {
            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "stock:" + trainId + ":" + startStation + ":" + endStation;
            boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
            if (!lockAcquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                // 双重检查
                cachedJSON = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cachedJSON != null) {
                    availableSeats = JSON.parseArray(cachedJSON, Seat.class);
                } else {
                    List<TrainStation> stations = trainStationMapper.selectList(
                            Wrappers.lambdaQuery(TrainStation.class)
                                    .eq(TrainStation::getTrainId, trainId)
                    );
                    long queryMask = StationCalculateUtil.bitmapMask(stations, startStation, endStation);
                    availableSeats = seatMapper.selectList(
                            Wrappers.lambdaQuery(Seat.class)
                                    .eq(Seat::getTrainId, trainId)
                                    .apply("(seat_bitmap & {0}) = 0", queryMask)
                    );
                    stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(availableSeats),
                            RedisConstant.CACHE_TTL_SEAT_STOCK + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_SEAT_STOCK / 10),
                            TimeUnit.SECONDS);
                }
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        Map<Integer, Long> availableByType = availableSeats.stream()
                .filter(s -> requiredByType.containsKey(s.getSeatType()))
                .collect(Collectors.groupingBy(Seat::getSeatType, Collectors.counting()));

        requiredByType.forEach((seatType, required) -> {
            Long available = availableByType.getOrDefault(seatType, 0L);
            if (available < required) {
                throw new ClientException("余票不足");
            }
        });
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
