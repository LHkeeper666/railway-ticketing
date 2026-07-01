package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatClassDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Waitlist;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.WaitlistStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.WaitlistMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WaitlistCreateParamVerifyChainHandler implements WaitlistCreateChainFilter<WaitlistCreateReqDTO> {

    private final OrderMapper orderMapper;
    private final WaitlistMapper waitlistMapper;
    private final SeatMapper seatMapper;
    private final TrainStationService trainStationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    @Override
    public void handler(WaitlistCreateReqDTO req) {
        Long trainId = Long.valueOf(req.getTrainId());
        Long userId = UserContext.get().getUserId();
        String startStation = req.getStartStation();
        String endStation = req.getEndStation();
        Integer seatType = req.getSeatType();

        // 1. 检查同一区间是否有 UNPAID/PAID/PENDING 订单
        List<Integer> activeStatuses = Arrays.asList(
                OrderStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.PENDING.getCode()
        );
        Order existingOrder = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getUserId, userId)
                        .eq(Order::getTrainId, trainId)
                        .eq(Order::getStartStation, startStation)
                        .eq(Order::getEndStation, endStation)
                        .in(Order::getStatus, activeStatuses)
        );
        if (existingOrder != null) {
            throw new ClientException("您已有该区间的有效订单，请勿重复提交");
        }

        // 2. 检查同一区间是否有 WAITING 候补
        Waitlist existingWaitlist = waitlistMapper.selectOne(
                Wrappers.lambdaQuery(Waitlist.class)
                        .eq(Waitlist::getUserId, userId)
                        .eq(Waitlist::getTrainId, trainId)
                        .eq(Waitlist::getStartStation, startStation)
                        .eq(Waitlist::getEndStation, endStation)
                        .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
        );
        if (existingWaitlist != null) {
            throw new ClientException("您已有候补订单，请勿重复提交");
        }

        // 3. 检查该座位类型确实无余票
        List<TrainStation> stations = trainStationService.getTrainStationsByTrainId(trainId);
        long queryMask = StationCalculateUtil.bitmapMask(stations, startStation, endStation);

        // 先查缓存
        String cacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING, trainId, startStation, endStation);
        String cachedJSON = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJSON != null) {
            List<SeatClassDTO> seatClassList = JSON.parseArray(cachedJSON, SeatClassDTO.class);
            long available = seatClassList.stream()
                    .filter(dto -> dto.getType().equals(seatType))
                    .mapToLong(SeatClassDTO::getQuantity)
                    .findFirst()
                    .orElse(0);
            if (available > 0) {
                throw new ClientException("该座位类型有余票，请直接预订");
            }
            return;
        }

        // 缓存未命中，查 DB
        DistributedLock lock = lockFactory.tryLock(
                "stock:" + trainId + ":" + startStation + ":" + endStation,
                RedisConstant.LOCK_TTL_SECONDS);
        if (lock == null) {
            throw new ServiceException("系统正忙，请稍后重试");
        }
        try {
            List<Seat> seats = seatMapper.selectList(
                    Wrappers.lambdaQuery(Seat.class)
                            .eq(Seat::getTrainId, trainId)
                            .eq(Seat::getSeatType, seatType)
                            .apply("(seat_bitmap & {0}) = 0", queryMask)
            );
            if (!seats.isEmpty()) {
                throw new ClientException("该座位类型有余票，请直接预订");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
