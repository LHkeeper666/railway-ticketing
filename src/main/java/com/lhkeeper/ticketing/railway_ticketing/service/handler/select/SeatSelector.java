package com.lhkeeper.ticketing.railway_ticketing.service.handler.select;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SeatSelector {

    private final PassengerMapper passengerMapper;
    private final SeatMapper seatMapper;

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

        List<Seat> updateSeats = new ArrayList<>();

        // 选择座位
        for (TicketDTO ticketDTO : ticketDTOList) {
            Seat chosenSeat = seatMapper.selectList(
                    Wrappers.lambdaQuery(Seat.class)
                            .eq(Seat::getSeatType, ticketDTO.getSeatType())
                            .eq(Seat::getTrainId, orderCreateReqDTO.getTrainId())
                            .eq(Seat::getStartStation, orderCreateReqDTO.getStartStation())
                            .eq(Seat::getEndStation, orderCreateReqDTO.getEndStation())
                            .eq(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
            ).get(0);
            if (chosenSeat == null)
                throw new ServiceException("余票不足");

            chosenSeat.setSeatStatus(SeatStatusEnum.LOCKED.getCode());
            ticketDTO.setSeatNumber(chosenSeat.getSeatNumber());
            ticketDTO.setAmount(chosenSeat.getPrice());
            ticketDTO.setCarriageNumber(chosenSeat.getCarriageNumber());

            updateSeats.add(chosenSeat);
        }

        seatMapper.updateById(updateSeats);

        return ticketDTOList;
    }
}
