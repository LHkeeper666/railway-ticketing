package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatus;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final SeatSelector seatSelector;
    private final OrderItemMapper orderItemMapper;
    private final TicketMapper ticketMapper;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO) {
        // TODO: 参数检验

        // 创建订单
        Order order = Order.builder()
                .orderSn(null) // TODO
                .orderTime(LocalDateTime.now())
                .startStation(reqDTO.getStartStation())
                .endStation(reqDTO.getEndStation())
                .trainId(Long.valueOf(reqDTO.getTrainId()))
                .userId(null) // TODO
                .username(null) // TODO
                .trainNumber(trainMapper.selectOne(
                        Wrappers.lambdaQuery(Train.class)
                                .eq(Train::getId, reqDTO.getTrainId())
                ).getTrainNumber())
                .departureTime(trainStationMapper.selectOne(
                        Wrappers.lambdaQuery(TrainStation.class)
                                .eq(TrainStation::getTrainId, reqDTO.getTrainId())
                                .eq(TrainStation::getStartStation, reqDTO.getStartStation())
                ).getDepartureTime())
                .arrivalTime(trainStationMapper.selectOne(
                        Wrappers.lambdaQuery(TrainStation.class)
                                .eq(TrainStation::getTrainId, reqDTO.getTrainId())
                                .eq(TrainStation::getStartStation, reqDTO.getEndStation())
                ).getArrivalTime())
                .status(OrderStatus.UNPAID.getCode())
                .build();

        // 选择并锁定座位
        List<TicketDTO> ticketDTOs = null;
        try {
            ticketDTOs = seatSelector.selectSeats(reqDTO);
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }

        // 生成 orderitem
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemDTO> orderItemDTOs = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();

        for (TicketDTO ticketDTO : ticketDTOs) {
            orderItems.add(OrderItem.builder()
                    .orderSn(null) // TODO
                    .phone(ticketDTO.getPhone())
                    .userId(null) // TODO
                    .username(null) // TODO
                    .trainId(Long.parseLong(reqDTO.getTrainId()))
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatType(ticketDTO.getSeatType())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .realName(ticketDTO.getRealName())
                    .idType(ticketDTO.getIdType())
                    .idCard(ticketDTO.getIdCard())
                    .ticketType(ticketDTO.getUserType())
                    .phone(ticketDTO.getPhone())
                    .status(TicketStatusEnum.UNPAID.getCode())
                    .amount(ticketDTO.getAmount())
                    .build()
            );
            orderItemDTOs.add(OrderItemDTO.builder()
                    .seatType(ticketDTO.getSeatType())
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .realName(ticketDTO.getRealName())
                    .idType(ticketDTO.getIdType())
                    .idCard(ticketDTO.getIdCard())
                    .ticketType(ticketDTO.getUserType())
                    .amount(ticketDTO.getAmount())
                    .build()
            );
            tickets.add(Ticket.builder()
                    .username(null)
                    .trainId(Long.parseLong(reqDTO.getTrainId()))
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .passengerId(Long.parseLong(ticketDTO.getPassengerId()))
                    .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                    .build()
            );
        }

        // insert
        orderMapper.insert(order);
        ticketMapper.insert(tickets);
        orderItemMapper.insert(orderItems);

        return OrderCreateRespDTO.builder()
                .orderSn(order.getOrderSn())
                .orderItemDTOS(orderItemDTOs)
                .build();
    }
}
