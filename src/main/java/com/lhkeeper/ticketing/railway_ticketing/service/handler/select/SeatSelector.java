package com.lhkeeper.ticketing.railway_ticketing.service.handler.select;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.RouteDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 座位选择器，负责选座锁定、全区间占座及缓存失效
 * TODO: 座位选择这块应该还可以再优化，目前似乎会有多个订单互相冲突的问题
 */
@Component
@RequiredArgsConstructor
public class SeatSelector {

    private final PassengerMapper passengerMapper;
    private final SeatMapper seatMapper;
    private final TrainStationMapper trainStationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 普通购票选座，委托至 selectAndLockSeats */
    public List<TicketDTO> selectSeats(OrderCreateReqDTO orderCreateReqDTO) throws ServiceException {
        return selectAndLockSeats(
                Long.parseLong(orderCreateReqDTO.getTrainId()),
                orderCreateReqDTO.getStartStation(),
                orderCreateReqDTO.getEndStation(),
                orderCreateReqDTO.getPassengers()
        );
    }

    /** 抢票选座并锁定：按座位类型分组 → 分布式锁 → 查可用座位 → 锁定全部重叠区间 */
    public List<TicketDTO> selectAndLockSeats(Long trainId, String startStation, String endStation,
                                              List<OrderCreatePassengerDetailDTO> passengers) throws ServiceException {
        List<String> passengerIds = passengers.stream()
                .map(OrderCreatePassengerDetailDTO::getPassengerId).toList();

        List<Passenger> passengerDOs = passengerMapper.selectByIds(passengerIds);
        if (passengerDOs.isEmpty()) {
            throw new ClientException("无乘车人");
        }
        Map<Long, Passenger> idToPassenger = passengerDOs.stream()
                .collect(Collectors.toMap(
                        Passenger::getId,
                        Function.identity()
                ));

        List<TicketDTO> ticketDTOList = new ArrayList<>();
        passengers.forEach(passenger -> {
            Passenger passengerDO = idToPassenger.get(Long.parseLong(passenger.getPassengerId()));
            ticketDTOList.add(TicketDTO.builder()
                    .seatType(passenger.getSeatType())
                    .passengerId(passenger.getPassengerId())
                    .phone(passengerDO.getPhone())
                    .idType(passengerDO.getIdType())
                    .idCard(passengerDO.getIdCard())
                    .realName(passengerDO.getRealName())
                    .userType(passengerDO.getDiscountType())
                    .build()
            );
        });

        Map<Integer, List<TicketDTO>> groupedBySeatType = ticketDTOList.stream()
                .collect(Collectors.groupingBy(TicketDTO::getSeatType));

        String trainIdStr = String.valueOf(trainId);
        for (Map.Entry<Integer, List<TicketDTO>> entry : groupedBySeatType.entrySet()) {
            Integer seatType = entry.getKey();
            List<TicketDTO> sameTypeTickets = entry.getValue();
            int needCount = sameTypeTickets.size();

            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "seat:" + trainIdStr + ":"
                    + startStation + ":" + endStation + ":" + seatType;
            boolean acquired = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS)
            );
            if (!acquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            try {
                List<Seat> availableSeats = seatMapper.selectList(
                        Wrappers.lambdaQuery(Seat.class)
                                .eq(Seat::getSeatType, seatType)
                                .eq(Seat::getTrainId, trainId)
                                .eq(Seat::getStartStation, startStation)
                                .eq(Seat::getEndStation, endStation)
                                .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                );
                if (availableSeats.size() < needCount) {
                    throw new ClientException("余票不足");
                }

                List<TrainStation> trainStations = trainStationMapper.selectList(
                        Wrappers.lambdaQuery(TrainStation.class)
                                .eq(TrainStation::getTrainId, trainId)
                );
                List<RouteDTO> takeoutRoutes = StationCalculateUtil.takeoutStation(
                        trainStations, startStation, endStation);

                for (int i = 0; i < needCount; i++) {
                    Seat chosenSeat = availableSeats.get(i);
                    TicketDTO ticketDTO = sameTypeTickets.get(i);

                    ticketDTO.setSeatNumber(chosenSeat.getSeatNumber());
                    ticketDTO.setAmount(chosenSeat.getPrice());
                    ticketDTO.setCarriageNumber(chosenSeat.getCarriageNumber());

                    for (RouteDTO route : takeoutRoutes) {
                        LambdaUpdateWrapper<Seat> wrapper = new LambdaUpdateWrapper<Seat>()
                                .eq(Seat::getTrainId, trainId)
                                .eq(Seat::getCarriageNumber, chosenSeat.getCarriageNumber())
                                .eq(Seat::getSeatNumber, chosenSeat.getSeatNumber())
                                .eq(Seat::getStartStation, route.getStartStation())
                                .eq(Seat::getEndStation, route.getEndStation())
                                .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                                .set(Seat::getSeatStatus, SeatStatusEnum.LOCKED.getCode());
                        int updated = seatMapper.update(null, wrapper);
                        if (updated == 0) {
                            throw new ServiceException("座位已被抢占");
                        }
                    }
                }
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        List<TrainStation> allStations = trainStationMapper.selectList(
                Wrappers.lambdaQuery(TrainStation.class)
                        .eq(TrainStation::getTrainId, trainId)
        );
        List<RouteDTO> cacheInvalidateRoutes = StationCalculateUtil.takeoutStation(
                allStations, startStation, endStation);
        for (RouteDTO route : cacheInvalidateRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    trainIdStr, route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }

        return ticketDTOList;
    }
}
