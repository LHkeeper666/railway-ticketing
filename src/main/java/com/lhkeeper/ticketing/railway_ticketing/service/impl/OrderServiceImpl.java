package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.FlashOrderMessageDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.RouteDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.FlashOrderCreateRespDTO;
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
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

/**
 * 订单服务实现，处理订单创建、抢票排队、支付、取消及超时管理
 */
@Slf4j
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
    private final DistributedLockFactory lockFactory;
    private final RabbitTemplate rabbitTemplate;
    private final TicketServiceImpl ticketServiceImpl;
    private final OrderItemServiceImpl orderItemServiceImpl;

    /**
     * 创建普通购票订单：责任链校验 → 选座锁定 → 写 Order/OrderItem/Ticket → 发送超时取消消息
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public OrderCreateRespDTO createOrder(OrderCreateReqDTO reqDTO) {
        orderCreateChainContext.handler(ChainMarkEnum.ORDER_CREATE.name(), reqDTO);
        String orderSn = String.valueOf(snowflakeUtil.generateId());
        log.info("开始创建订单, orderSn={}, trainId={}, passengers={}, userId={}",
                orderSn, reqDTO.getTrainId(), reqDTO.getPassengers().size(), UserContext.get().getUserId());

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

        sendOrderTimeoutMessage(orderSn);

        log.info("订单创建成功, orderSn={}", orderSn);
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

    /**
     * 抢票排队：责任链校验 → 写 Order(PENDING) → 发送 MQ 消息 → 立即返回
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public FlashOrderCreateRespDTO flashCreateOrder(OrderCreateReqDTO reqDTO) {
        orderCreateChainContext.handler(ChainMarkEnum.ORDER_CREATE.name(), reqDTO);
        String orderSn = String.valueOf(snowflakeUtil.generateId());
        Long trainId = Long.valueOf(reqDTO.getTrainId());

        Order order = buildOrder(reqDTO, orderSn);
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        orderMapper.insert(order);

        FlashOrderMessageDTO msg = FlashOrderMessageDTO.builder()
                .orderSn(orderSn)
                .trainId(trainId)
                .startStation(reqDTO.getStartStation())
                .endStation(reqDTO.getEndStation())
                .passengers(reqDTO.getPassengers())
                .build();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        rabbitTemplate.convertAndSend(RabbitMQConfig.FLASH_ORDER_EXCHANGE,
                                RabbitMQConfig.FLASH_ORDER_ROUTING_KEY, msg);
                        log.info("抢票消息已发送, orderSn={}", orderSn);
                    }
                });

        return FlashOrderCreateRespDTO.builder()
                .orderSn(orderSn)
                .message("已进入排队，请稍后查询订单状态")
                .build();
    }

    /**
     * 处理抢票消息（MQ 消费端）：幂等检查 → 选座并锁定 → 写 OrderItem/Ticket → 发送超时消息
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void processFlashOrder(FlashOrderMessageDTO msg) {
        String orderSn = msg.getOrderSn();
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
                        .last("FOR UPDATE")
        );
        if (order == null) {
            log.warn("抢票订单尚未提交(事务未提交或主从延迟), orderSn={}", orderSn);
            throw new ServiceException("订单尚未提交，稍后重试");
        }
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            log.info("订单已处理（幂等跳过）, orderSn={}, status={}", orderSn, order.getStatus());
            return;
        }

        try {
            List<TicketDTO> ticketDTOs = seatSelector.selectAndLockSeats(
                    msg.getTrainId(), msg.getStartStation(), msg.getEndStation(), msg.getPassengers());

            List<OrderItem> orderItems = new ArrayList<>();
            List<Ticket> tickets = new ArrayList<>();
            for (TicketDTO ticketDTO : ticketDTOs) {
                orderItems.add(OrderItem.builder()
                        .orderSn(orderSn)
                        .phone(ticketDTO.getPhone())
                        .userId(order.getUserId())
                        .username(order.getUsername())
                        .trainId(msg.getTrainId())
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
                tickets.add(Ticket.builder()
                        .orderSn(orderSn)
                        .username(order.getUsername())
                        .trainId(msg.getTrainId())
                        .carriageNumber(ticketDTO.getCarriageNumber())
                        .seatNumber(ticketDTO.getSeatNumber())
                        .passengerId(Long.parseLong(ticketDTO.getPassengerId()))
                        .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                        .build()
                );
            }

            orderItemServiceImpl.saveBatch(orderItems);
            ticketServiceImpl.saveBatch(tickets);
            order.setStatus(OrderStatusEnum.UNPAID.getCode());
            orderMapper.updateById(order);

            sendOrderTimeoutMessage(orderSn);

            log.info("抢票订单处理成功, orderSn={}", orderSn);
        } catch (ClientException e) {
            log.warn("抢票订单业务失败, orderSn={}, msg={}", orderSn, e.getMessage());
            order.setStatus(OrderStatusEnum.CANCELED.getCode());
            orderMapper.updateById(order);
        }
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
        DistributedLock lock = lockFactory.tryLock("train:" + trainId, RedisConstant.LOCK_TTL_SECONDS);
        if (lock == null) {
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
            lock.unlock();
        }
    }

    private void sendOrderTimeoutMessage(String orderSn) {
        try {
            Message message = MessageBuilder
                    .withBody(orderSn.getBytes(StandardCharsets.UTF_8))
                    .setExpiration(RabbitMQConfig.ORDER_TIMEOUT_MS)
                    .build();
            rabbitTemplate.send(
                    RabbitMQConfig.ORDER_TIMEOUT_DELAY_EXCHANGE,
                    RabbitMQConfig.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                    message);
            log.info("超时取消消息已发送, orderSn={}, ttl={}ms", orderSn,
                    RabbitMQConfig.ORDER_TIMEOUT_MS);
        } catch (Exception e) {
            log.error("发送超时取消消息失败, orderSn={}", orderSn, e);
        }
    }

    /**
     * 模拟支付：责任链校验 → 计算订单总金额 → 生成 Pay 记录
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void payOrder(String orderSn) {
        orderPayChainContext.handler(ChainMarkEnum.ORDER_PAY.name(), orderSn);

        log.info("开始支付, orderSn={}", orderSn);
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
        log.info("支付记录已创建, orderSn={}, paySn={}, amount={}", orderSn, pay.getPaySn(), totalAmount);
    }

    /**
     * 处理支付回调：责任链校验 → 幂等检查 → 更新 Order/OrderItem/Ticket/Pay 状态
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void handlePayNotify(PayCallbackReqDTO reqDTO) {
        payNotifyChainContext.handler(ChainMarkEnum.PAY_NOTIFY.name(), reqDTO);

        log.info("收到支付回调, orderSn={}, status={}, tradeNo={}",
                reqDTO.getOrderSn(), reqDTO.getStatus(), reqDTO.getTradeNo());
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, reqDTO.getOrderSn())
        );
        if (order == null) {
            log.warn("支付回调-订单不存在, orderSn={}", reqDTO.getOrderSn());
            throw new ClientException("订单不存在");
        }
        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            log.warn("支付回调-订单已取消, orderSn={}", reqDTO.getOrderSn());
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
            log.info("支付成功, orderSn={}", reqDTO.getOrderSn());
        } else {
            log.warn("支付失败, orderSn={}", reqDTO.getOrderSn());
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

    /**
     * 查询订单详情，组装订单项和支付信息
     */
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

    /**
     * 取消订单（手动取消，允许退票 PAID 订单）
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void cancelOrder(String orderSn) {
        cancelOrder(orderSn, false);
    }

    /**
     * 取消订单：责任链校验 → FOR UPDATE 加锁 → 超时取消守卫 → 释放座位 → 更新状态 → 失效缓存
     * @param timeoutCancel true=超时取消（仅允许 UNPAID），false=手动取消（允许退票 PAID）
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void cancelOrder(String orderSn, boolean timeoutCancel) {
        orderCancelChainContext.handler(ChainMarkEnum.ORDER_CANCEL.name(), orderSn);

        log.info("开始取消订单, orderSn={}, timeoutCancel={}", orderSn, timeoutCancel);
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
                        .last("FOR UPDATE")
        );
        if (order == null) {
            log.warn("取消订单-订单不存在, orderSn={}", orderSn);
            throw new ClientException("订单不存在");
        }
        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            log.warn("取消订单-订单已取消, orderSn={}", orderSn);
            if (timeoutCancel) {
                return;
            }
            throw new ClientException("订单已取消");
        }
        if (timeoutCancel && !OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
            log.info("超时取消-订单状态已变更，跳过, orderSn={}, status={}", orderSn, order.getStatus());
            return;
        }

        boolean wasPaid = OrderStatusEnum.PAID.getCode().equals(order.getStatus());

        // 计算购买区间的位图掩码
        List<TrainStation> trainStations = trainStationMapper.selectList(
                Wrappers.lambdaQuery(TrainStation.class)
                        .eq(TrainStation::getTrainId, order.getTrainId())
        );
        long purchaseMask = StationCalculateUtil.bitmapMask(
                trainStations, order.getStartStation(), order.getEndStation());

        // 位图释放座位：清除购买区间对应的位
        List<Ticket> tickets = ticketMapper.selectList(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
        );
        for (Ticket ticket : tickets) {
            seatMapper.update(null,
                    Wrappers.lambdaUpdate(Seat.class)
                            .eq(Seat::getTrainId, ticket.getTrainId())
                            .eq(Seat::getCarriageNumber, ticket.getCarriageNumber())
                            .eq(Seat::getSeatNumber, ticket.getSeatNumber())
                            .apply("(seat_bitmap & {0}) = {0}", purchaseMask)
                            .setSql("seat_bitmap = seat_bitmap & ~" + purchaseMask)
            );
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
        List<RouteDTO> takeoutRoutes = StationCalculateUtil.takeoutStation(
                trainStations, order.getStartStation(), order.getEndStation());
        for (RouteDTO route : takeoutRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    order.getTrainId(), route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }

        log.info("订单已取消, orderSn={}, wasPaid={}, itemStatus={}", orderSn, wasPaid, itemStatus);
    }
}
