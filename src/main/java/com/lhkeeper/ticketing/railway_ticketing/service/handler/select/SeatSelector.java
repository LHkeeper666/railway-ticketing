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
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationPrice;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationPriceMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 座位选择器，基于位图模型选座并原子锁定
 */
@Component
@RequiredArgsConstructor
public class SeatSelector {

    private final PassengerMapper passengerMapper;
    private final SeatMapper seatMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final TrainStationService trainStationService;
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

    /** 抢票选座并锁定：按座位类型分组 → 计算位图掩码 → 位图查询可用座位 → CAS 原子锁定（冲突重试） */
    public List<TicketDTO> selectAndLockSeats(Long trainId, String startStation, String endStation,
                                              List<OrderCreatePassengerDetailDTO> passengers) throws ServiceException, ClientException {
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

        List<TrainStation> trainStations = trainStationService.getTrainStationsByTrainId(trainId);
        long purchaseMask = StationCalculateUtil.bitmapMask(trainStations, startStation, endStation);

        String trainIdStr = String.valueOf(trainId);
        for (Map.Entry<Integer, List<TicketDTO>> entry : groupedBySeatType.entrySet()) {
            Integer seatType = entry.getKey();
            List<TicketDTO> sameTypeTickets = entry.getValue();
            int needCount = sameTypeTickets.size();

            BigDecimal price = getPrice(trainId, startStation, endStation, seatType);

            List<Seat> availableSeats = seatMapper.selectList(
                    Wrappers.lambdaQuery(Seat.class)
                            .eq(Seat::getTrainId, trainId)
                            .eq(Seat::getSeatType, seatType)
                            .apply("(seat_bitmap & {0}) = 0", purchaseMask)
                            .last("LIMIT " + (needCount * 3))
            );
            if (availableSeats.size() < needCount) {
                throw new ClientException("余票不足");
            }

            int acquired = 0;
            for (Seat chosenSeat : availableSeats) {
                LambdaUpdateWrapper<Seat> lockWrapper = new LambdaUpdateWrapper<Seat>()
                        .eq(Seat::getId, chosenSeat.getId())
                        .apply("(seat_bitmap & {0}) = 0", purchaseMask)
                        .setSql("seat_bitmap = seat_bitmap | " + purchaseMask);
                int updated = seatMapper.update(null, lockWrapper);
                if (updated == 0) {
                    continue;
                }

                TicketDTO ticketDTO = sameTypeTickets.get(acquired);
                ticketDTO.setSeatNumber(chosenSeat.getSeatNumber());
                ticketDTO.setAmount(price);
                ticketDTO.setCarriageNumber(chosenSeat.getCarriageNumber());

                acquired++;
                if (acquired == needCount) {
                    break;
                }
            }
            if (acquired < needCount) {
                throw new ClientException("余票不足");
            }
        }

        List<RouteDTO> cacheInvalidateRoutes = StationCalculateUtil.takeoutStation(
                trainStations, startStation, endStation);
        for (RouteDTO route : cacheInvalidateRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    trainIdStr, route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }

        return ticketDTOList;
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
}
