package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final PayMapper payMapper;
    private final TicketMapper ticketMapper;
    private final AbstractChainContext<OrderCreateReqDTO> orderCreateChainContext;
    private final AbstractChainContext<String> orderPayChainContext;
    private final SnowflakeUtil snowflakeUtil;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO) {
        orderCreateChainContext.handler(ChainMarkEnum.ORDER_CREATE.name(), reqDTO);
        String orderSn = String.valueOf(snowflakeUtil.generateId());
        // 创建订单
        Order order = Order.builder()
                .orderSn(orderSn)
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
                .status(OrderStatusEnum.UNPAID.getCode())
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
                    .orderSn(orderSn)
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
                    .orderSn(orderSn)
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

    @Override
    public void payOrder(String orderSn) {
        // 参数校验
        orderPayChainContext.handler(ChainMarkEnum.ORDER_PAY.name(), orderSn);

        // 更新状态
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        order.setStatus(OrderStatusEnum.PAID.getCode());


        List<OrderItem> orderItems = orderItemMapper.selectList(
                Wrappers.lambdaQuery(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
        );
        BigDecimal totalAmount = BigDecimal.ZERO;
        orderItems.forEach(orderItem -> {
            orderItem.setStatus(OrderStatusEnum.PAID.getCode());
            totalAmount.add(orderItem.getAmount());
        });

        List<Ticket> tickets = ticketMapper.selectList(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
        );
        tickets.forEach(ticket -> {
            ticket.setTicketStatus(TicketStatusEnum.PAID.getCode());
        });

        // 创建pay实体
        // TODO: pay 实体涉及比较多目前没用上的字段
        Pay pay = Pay.builder()
                .paySn(null) // TODO
                .orderSn(orderSn)
                .outOrderSn(null) // TODO
                .totalAmount(totalAmount)
                .build();

        orderMapper.updateById(order);
        orderItemMapper.updateById(orderItems);
        ticketMapper.updateById(tickets);
        payMapper.insert(pay);
    }
}
