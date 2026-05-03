package com.lhkeeper.ticketing.railway_ticketing.service.handler.select;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SeatSelector {

    private final PassengerMapper passengerMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public List<TicketDTO> selectSeats(OrderCreateReqDTO orderCreateReqDTO) throws ServiceException {
        List<String> passengerIds = orderCreateReqDTO.getPassengers().stream()
                .map(OrderCreatePassengerDetailDTO::getPassengerId).toList();

        List<Passenger> passengers = passengerMapper.selectByIds(passengerIds);
        if (passengers.isEmpty()) {
            throw new ServiceException("无乘车人");
        }
        Map<Long, Passenger> idToPassenger = passengers.stream()
                .collect(Collectors.toMap(
                        Passenger::getId,
                        Function.identity()
                ));

        List<TicketDTO> ticketDTOList = new ArrayList<>();
        orderCreateReqDTO.getPassengers().forEach(passenger -> {
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

        // 按座位类型分组
        Map<Integer, List<TicketDTO>> groupedBySeatType = ticketDTOList.stream()
                .collect(Collectors.groupingBy(TicketDTO::getSeatType));

        for (Map.Entry<Integer, List<TicketDTO>> entry : groupedBySeatType.entrySet()) {
            Integer seatType = entry.getKey();
            List<TicketDTO> sameTypeTickets = entry.getValue();
            int needCount = sameTypeTickets.size();

            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "seat:" + orderCreateReqDTO.getTrainId() + ":"
                    + orderCreateReqDTO.getStartStation() + ":" + orderCreateReqDTO.getEndStation()
                    + ":" + seatType;
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
                                .eq(Seat::getTrainId, orderCreateReqDTO.getTrainId())
                                .eq(Seat::getStartStation, orderCreateReqDTO.getStartStation())
                                .eq(Seat::getEndStation, orderCreateReqDTO.getEndStation())
                                .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                );
                if (availableSeats.size() < needCount) {
                    throw new ServiceException("余票不足");
                }

                for (int i = 0; i < needCount; i++) {
                    Seat chosenSeat = availableSeats.get(i);
                    TicketDTO ticketDTO = sameTypeTickets.get(i);

                    chosenSeat.setSeatStatus(SeatStatusEnum.LOCKED.getCode());
                    ticketDTO.setSeatNumber(chosenSeat.getSeatNumber());
                    ticketDTO.setAmount(chosenSeat.getPrice());
                    ticketDTO.setCarriageNumber(chosenSeat.getCarriageNumber());

                    LambdaUpdateWrapper<Seat> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(Seat::getId, chosenSeat.getId())
                            .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode());
                    boolean updated = seatMapper.update(chosenSeat, wrapper) > 0;
                    if (!updated) {
                        throw new ServiceException("座位已被抢占");
                    }
                }
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        // 座位锁定后使余票缓存失效
        String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                orderCreateReqDTO.getTrainId(), orderCreateReqDTO.getStartStation(), orderCreateReqDTO.getEndStation());
        stringRedisTemplate.delete(stockCacheKey);

        return ticketDTOList;
    }
}
