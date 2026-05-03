package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final TicketServiceImpl ticketServiceImpl;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO) {
        orderCreateChainContext.handler(ChainMarkEnum.ORDER_CREATE.name(), reqDTO);
        String orderSn = String.valueOf(snowflakeUtil.generateId());

        Order order = buildOrder(reqDTO, orderSn);

        // 选择并锁定座位
        List<TicketDTO> ticketDTOs = seatSelector.selectSeats(reqDTO);

        // 生成 orderitem
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemDTO> orderItemDTOs = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();

        Long trainId = Long.parseLong(reqDTO.getTrainId());
        for (TicketDTO ticketDTO : ticketDTOs) {
            orderItems.add(OrderItem.builder()
                    .orderSn(orderSn)
                    .phone(ticketDTO.getPhone())
                    .userId(null) // TODO
                    .username(null) // TODO
                    .trainId(trainId)
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatType(ticketDTO.getSeatType())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .realName(ticketDTO.getRealName())
                    .idType(ticketDTO.getIdType())
                    .idCard(ticketDTO.getIdCard())
                    .ticketType(ticketDTO.getUserType())
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
                    .trainId(trainId)
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .passengerId(Long.parseLong(ticketDTO.getPassengerId()))
                    .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                    .build()
            );
        }

        // insert
        orderMapper.insert(order);
//        ticketMapper.insert(tickets);
        ticketServiceImpl.saveBatch(tickets);
        orderItemMapper.insert(orderItems);

        return OrderCreateRespDTO.builder()
                .orderSn(order.getOrderSn())
                .orderItemDTOS(orderItemDTOs)
                .build();
    }

    private Order buildOrder(OrderCreateReqDTO reqDTO, String orderSn) {
        Long trainId = Long.valueOf(reqDTO.getTrainId());
        String startStation = reqDTO.getStartStation();
        String endStation = reqDTO.getEndStation();

        Train train = getTrainInfo(trainId);

        String depKey = String.format(RedisConstant.TRAIN_STATION_MAPPING, trainId, startStation);
        String arrKey = String.format(RedisConstant.TRAIN_STATION_MAPPING, trainId, endStation);
        List<String> tsCache = stringRedisTemplate.opsForValue().multiGet(Arrays.asList(depKey, arrKey));
        TrainStation depTS = null, arrTS = null;

        if (tsCache.get(0) != null && tsCache.get(1) != null) {
            depTS = JSON.parseObject(tsCache.get(0), TrainStation.class);
            arrTS = JSON.parseObject(tsCache.get(1), TrainStation.class);
        } else {
            List<TrainStation> stations = trainStationMapper.selectList(
                    Wrappers.lambdaQuery(TrainStation.class)
                            .eq(TrainStation::getTrainId, trainId)
                            .in(TrainStation::getStartStation, startStation, endStation)
            );
            for (TrainStation ts : stations) {
                if (startStation.equals(ts.getStartStation())) depTS = ts;
                if (endStation.equals(ts.getStartStation())) arrTS = ts;
                stringRedisTemplate.opsForValue().set(
                        String.format(RedisConstant.TRAIN_STATION_MAPPING, trainId, ts.getStartStation()),
                        JSON.toJSONString(ts)
                );
            }
            if (depTS == null || arrTS == null) {
                throw new ClientException("车次区间信息不存在");
            }
        }

        return Order.builder()
                .orderSn(orderSn)
                .orderTime(LocalDateTime.now())
                .trainId(trainId)
                .userId(null)
                .username(null)
                .startStation(startStation)
                .endStation(endStation)
                .trainNumber(train.getTrainNumber())
                .departureTime(depTS.getDepartureTime())
                .arrivalTime(arrTS.getArrivalTime())
                .status(OrderStatusEnum.UNPAID.getCode())
                .build();
    }

    private Train getTrainInfo(Long trainId) {
        String trainKey = String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, trainId);
        String trainJSON = stringRedisTemplate.opsForValue().get(trainKey);
        if (trainJSON != null) {
            return JSON.parseObject(trainJSON, Train.class);
        }
        Train train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new ClientException("车次不存在");
        }
        stringRedisTemplate.opsForValue().set(trainKey, JSON.toJSONString(train));
        return train;
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
