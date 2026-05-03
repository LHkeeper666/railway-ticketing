package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.RouteDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderItemDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayInfoDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
    private final AbstractChainContext<PayCallbackReqDTO> payNotifyChainContext;
    private final AbstractChainContext<String> orderCancelChainContext;
    private final SeatMapper seatMapper;
    private final SnowflakeUtil snowflakeUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final TicketServiceImpl ticketServiceImpl;
    private final OrderItemServiceImpl orderItemServiceImpl;

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
                    .userId(UserContext.get().getUserId())
                    .username(UserContext.get().getUsername())
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
                    .username(UserContext.get().getUsername())
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
        ticketServiceImpl.saveBatch(tickets);
        orderItemServiceImpl.saveBatch(orderItems);

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

        if (tsCache != null && tsCache.get(0) != null && tsCache.get(1) != null) {
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
                        JSON.toJSONString(ts),
                        RedisConstant.CACHE_TTL_TRAIN_STATION + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_TRAIN_STATION / 10),
                        TimeUnit.SECONDS
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
                .userId(UserContext.get().getUserId())
                .username(UserContext.get().getUsername())
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
            if (RedisConstant.NULL_PLACEHOLDER.equals(trainJSON)) {
                throw new ClientException("车次不存在");
            }
            return JSON.parseObject(trainJSON, Train.class);
        }
        String lockKey = RedisConstant.LOCK_KEY_PREFIX + "train:" + trainId;
        boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
        if (!lockAcquired) {
            throw new ServiceException("系统正忙，请稍后重试");
        }
        try {
            trainJSON = stringRedisTemplate.opsForValue().get(trainKey);
            if (trainJSON != null) {
                if (RedisConstant.NULL_PLACEHOLDER.equals(trainJSON)) {
                    throw new ClientException("车次不存在");
                }
                return JSON.parseObject(trainJSON, Train.class);
            }
            Train train = trainMapper.selectById(trainId);
            if (train == null) {
                stringRedisTemplate.opsForValue().set(trainKey, RedisConstant.NULL_PLACEHOLDER,
                        RedisConstant.CACHE_TTL_NULL, TimeUnit.SECONDS);
                throw new ClientException("车次不存在");
            }
            stringRedisTemplate.opsForValue().set(trainKey, JSON.toJSONString(train),
                    RedisConstant.CACHE_TTL_TRAIN_INFO + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_TRAIN_INFO / 10),
                    TimeUnit.SECONDS);
            return train;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void payOrder(String orderSn) {
        orderPayChainContext.handler(ChainMarkEnum.ORDER_PAY.name(), orderSn);

        List<OrderItem> orderItems = orderItemMapper.selectList(
                Wrappers.lambdaQuery(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
        );
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItems) {
            totalAmount = totalAmount.add(orderItem.getAmount());
        }

        Pay pay = Pay.builder()
                .paySn(String.valueOf(snowflakeUtil.generateId()))
                .orderSn(orderSn)
                .totalAmount(totalAmount)
                .status("PENDING")
                .build();
        payMapper.insert(pay);
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void handlePayNotify(PayCallbackReqDTO reqDTO) {
        payNotifyChainContext.handler(ChainMarkEnum.PAY_NOTIFY.name(), reqDTO);

        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, reqDTO.getOrderSn())
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }
        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            throw new ClientException("订单已取消");
        }

        boolean success = "SUCCESS".equalsIgnoreCase(reqDTO.getStatus());

        if (success) {
            if (OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
                return;
            }
            order.setStatus(OrderStatusEnum.PAID.getCode());
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);

            orderItemMapper.update(null,
                    Wrappers.lambdaUpdate(OrderItem.class)
                            .eq(OrderItem::getOrderSn, reqDTO.getOrderSn())
                            .set(OrderItem::getStatus, TicketStatusEnum.PAID.getCode())
            );

            ticketMapper.update(null,
                    Wrappers.lambdaUpdate(Ticket.class)
                            .eq(Ticket::getOrderSn, reqDTO.getOrderSn())
                            .set(Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
            );
        }

        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, reqDTO.getOrderSn())
        );
        if (pay != null) {
            pay.setTradeNo(reqDTO.getTradeNo());
            pay.setChannel(reqDTO.getChannel());
            pay.setStatus(success ? "SUCCESS" : "FAIL");
            pay.setGmtPayment(success ? LocalDateTime.now() : null);
            if (reqDTO.getTotalAmount() != null) {
                pay.setTotalAmount(reqDTO.getTotalAmount());
            }
            payMapper.updateById(pay);
        }
    }

    @Override
    public OrderDetailRespDTO getOrderDetail(String orderSn) {
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                Wrappers.lambdaQuery(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
        );

        List<OrderItemDetailDTO> itemDTOs = orderItems.stream()
                .map(item -> OrderItemDetailDTO.builder()
                        .realName(item.getRealName())
                        .idType(item.getIdType())
                        .idCard(item.getIdCard())
                        .ticketType(item.getTicketType())
                        .seatType(item.getSeatType())
                        .carriageNumber(item.getCarriageNumber())
                        .seatNumber(item.getSeatNumber())
                        .amount(item.getAmount())
                        .status(item.getStatus())
                        .phone(item.getPhone())
                        .build())
                .toList();

        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
        );
        PayInfoDTO payInfo = null;
        if (pay != null) {
            payInfo = PayInfoDTO.builder()
                    .paySn(pay.getPaySn())
                    .channel(pay.getChannel())
                    .tradeNo(pay.getTradeNo())
                    .totalAmount(pay.getTotalAmount())
                    .status(pay.getStatus())
                    .gmtPayment(pay.getGmtPayment())
                    .build();
        }

        return OrderDetailRespDTO.builder()
                .orderSn(order.getOrderSn())
                .userId(order.getUserId())
                .username(order.getUsername())
                .trainId(order.getTrainId())
                .trainNumber(order.getTrainNumber())
                .ridingDate(order.getRidingDate())
                .startStation(order.getStartStation())
                .endStation(order.getEndStation())
                .departureTime(order.getDepartureTime())
                .arrivalTime(order.getArrivalTime())
                .status(order.getStatus())
                .orderTime(order.getOrderTime())
                .payTime(order.getPayTime())
                .orderItems(itemDTOs)
                .payInfo(payInfo)
                .build();
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void cancelOrder(String orderSn) {
        orderCancelChainContext.handler(ChainMarkEnum.ORDER_CANCEL.name(), orderSn);

        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }
        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            throw new ClientException("订单已取消");
        }

        boolean wasPaid = OrderStatusEnum.PAID.getCode().equals(order.getStatus());

        // 获取列车路线，计算全部重叠区间
        List<TrainStation> trainStations = trainStationMapper.selectList(
                Wrappers.lambdaQuery(TrainStation.class)
                        .eq(TrainStation::getTrainId, order.getTrainId())
        );
        List<RouteDTO> takeoutRoutes = StationCalculateUtil.takeoutStation(
                trainStations, order.getStartStation(), order.getEndStation());

        // 释放全部重叠区间的座位
        List<Ticket> tickets = ticketMapper.selectList(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
        );
        for (Ticket ticket : tickets) {
            for (RouteDTO route : takeoutRoutes) {
                seatMapper.update(null,
                        Wrappers.lambdaUpdate(Seat.class)
                                .eq(Seat::getTrainId, ticket.getTrainId())
                                .eq(Seat::getCarriageNumber, ticket.getCarriageNumber())
                                .eq(Seat::getSeatNumber, ticket.getSeatNumber())
                                .eq(Seat::getStartStation, route.getStartStation())
                                .eq(Seat::getEndStation, route.getEndStation())
                                .eq(Seat::getSeatStatus, SeatStatusEnum.LOCKED.getCode())
                                .set(Seat::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                );
            }
        }

        // 更新订单状态
        order.setStatus(OrderStatusEnum.CANCELED.getCode());
        orderMapper.updateById(order);

        // 更新订单项和车票状态
        Integer itemStatus = wasPaid ? TicketStatusEnum.REFUNDED.getCode() : TicketStatusEnum.CLOSED.getCode();
        orderItemMapper.update(null,
                Wrappers.lambdaUpdate(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
                        .set(OrderItem::getStatus, itemStatus)
        );
        ticketMapper.update(null,
                Wrappers.lambdaUpdate(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
                        .set(Ticket::getTicketStatus, itemStatus)
        );

        // 已支付则更新 Pay 为 REFUNDED
        if (wasPaid) {
            payMapper.update(null,
                    Wrappers.lambdaUpdate(Pay.class)
                            .eq(Pay::getOrderSn, orderSn)
                            .set(Pay::getStatus, "REFUNDED")
            );
        }

        // 失效全部重叠区间的余票缓存
        for (RouteDTO route : takeoutRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    order.getTrainId(), route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }
    }
}
