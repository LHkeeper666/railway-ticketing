package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderCreateStockChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    private final SeatMapper seatMapper;
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
            availableSeats = seatMapper.selectList(
                    Wrappers.lambdaQuery(Seat.class)
                            .eq(Seat::getTrainId, trainId)
                            .eq(Seat::getStartStation, startStation)
                            .eq(Seat::getEndStation, endStation)
                            .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
            );
            stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(availableSeats));
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
