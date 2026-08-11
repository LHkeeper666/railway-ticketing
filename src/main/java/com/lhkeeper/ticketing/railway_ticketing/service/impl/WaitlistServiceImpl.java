package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.TicketDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.WaitlistMatchMessageDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.statemachine.OrderStateMachine;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl extends ServiceImpl<WaitlistMapper, Waitlist> implements WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final WaitlistPassengerMapper waitlistPassengerMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final TicketMapper ticketMapper;
    private final PayMapper payMapper;
    private final TrainMapper trainMapper;
    private final PassengerMapper passengerMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final SeatSelector seatSelector;
    @Lazy
    @Autowired
    private OrderService orderService;
    private final SnowflakeUtil snowflakeUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DistributedLockFactory lockFactory;
    private final AbstractChainContext<WaitlistCreateReqDTO> waitlistCreateChainContext;
    private final OrderStateMachine stateMachine;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public WaitlistCreateRespDTO createWaitlist(WaitlistCreateReqDTO reqDTO) {
        waitlistCreateChainContext.handler(ChainMarkEnum.WAITLIST_CREATE.name(), reqDTO);

        Long trainId = Long.valueOf(reqDTO.getTrainId());
        Long userId = UserContext.get().getUserId();
        String username = UserContext.get().getUsername();
        String orderSn = String.valueOf(snowflakeUtil.generateId());
        String waitlistSn = String.valueOf(snowflakeUtil.generateId());

        // 1. 创建 Order (WAITLIST)
        Train train = trainMapper.selectById(trainId);
        Order order = Order.builder()
                .orderSn(orderSn)
                .orderTime(LocalDateTime.now())
                .trainId(trainId)
                .userId(userId)
                .username(username)
                .startStation(reqDTO.getStartStation())
                .endStation(reqDTO.getEndStation())
                .trainNumber(train.getTrainNumber())
                .status(OrderStatusEnum.WAITLIST.getCode())
                .build();
        orderMapper.insert(order);

        // 2. 创建 Pay (FROZEN)
        BigDecimal price = getPrice(trainId, reqDTO.getStartStation(), reqDTO.getEndStation(), reqDTO.getSeatType());
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(reqDTO.getPassengers().size()));
        Pay pay = Pay.builder()
                .paySn(String.valueOf(snowflakeUtil.generateId()))
                .orderSn(orderSn)
                .totalAmount(totalAmount)
                .status(PayStatusEnum.FROZEN.getCode())
                .build();
        payMapper.insert(pay);

        // 3. 创建 Waitlist
        // 候补截止时间：发车前 25 分钟（参考 12306）
        LocalDateTime departureTime = train.getDepartureTime();
        LocalDateTime expireTime = departureTime != null
                ? departureTime.minusMinutes(25)
                : LocalDateTime.now().plusDays(15);

        Waitlist waitlist = Waitlist.builder()
                .waitlistSn(waitlistSn)
                .orderSn(orderSn)
                .userId(userId)
                .trainId(trainId)
                .startStation(reqDTO.getStartStation())
                .endStation(reqDTO.getEndStation())
                .seatType(reqDTO.getSeatType())
                .passengerCount(reqDTO.getPassengers().size())
                .status(WaitlistStatusEnum.WAITING.getCode())
                .expireTime(expireTime)
                .build();
        waitlistMapper.insert(waitlist);

        // 4. 创建 WaitlistPassenger
        List<WaitlistPassenger> wpList = new ArrayList<>();
        for (var p : reqDTO.getPassengers()) {
            wpList.add(WaitlistPassenger.builder()
                    .waitlistId(waitlist.getId())
                    .passengerId(Long.valueOf(p.getPassengerId()))
                    .seatPreference(p.getSeatType() != null ? String.valueOf(p.getSeatType()) : null)
                    .build());
        }
        for (WaitlistPassenger wp : wpList) {
            waitlistPassengerMapper.insert(wp);
        }

        // 5. ZADD Redis 队列（事务提交后执行）
        String queueKey = buildQueueKey(trainId, reqDTO.getSeatType(), reqDTO.getStartStation(), reqDTO.getEndStation());
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            stringRedisTemplate.opsForZSet().add(queueKey, waitlistSn, (double) System.currentTimeMillis());
                            log.info("候补已加入Redis队列, waitlistSn={}, queueKey={}", waitlistSn, queueKey);
                        } catch (Exception e) {
                            log.warn("候补加入Redis队列失败(兜底任务会补偿), waitlistSn={}", waitlistSn, e);
                        }
                    }
                });

        log.info("候补订单创建成功, waitlistSn={}, orderSn={}, trainId={}", waitlistSn, orderSn, trainId);
        return WaitlistCreateRespDTO.builder()
                .waitlistSn(waitlistSn)
                .message("候补已提交，请等待系统为您分配座位")
                .build();
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void processWaitlist(Long trainId, Integer seatType, String startStation, String endStation) {
        String queueKey = buildQueueKey(trainId, seatType, startStation, endStation);

        // 获取分布式锁，防止同一维度并发匹配
        String lockKey = RedisConstant.WAITLIST_LOCK_PREFIX + trainId + ":" + seatType + ":" + startStation + ":" + endStation;
        DistributedLock lock = lockFactory.tryLock(lockKey, RedisConstant.LOCK_TTL_SECONDS);
        if (lock == null) {
            log.debug("获取候补匹配锁失败, 跳过, queueKey={}", queueKey);
            return;
        }

        try {
            // ZRANGE 取队列中的候补（按时间排序）
            Set<String> candidates = stringRedisTemplate.opsForZSet().range(queueKey, 0, -1);
            if (candidates == null || candidates.isEmpty()) {
                return;
            }

            for (String waitlistSn : candidates) {
                boolean processed = tryMatchOne(waitlistSn, trainId, seatType, startStation, endStation, queueKey);
                if (processed) {
                    // 匹配成功或过期，继续处理下一个
                    continue;
                }
                // 匹配失败（余票不足），停止处理后续
                break;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试匹配单个候补订单
     * @return true=已处理（成功/过期/跳过），false=余票不足应停止
     */
    private boolean tryMatchOne(String waitlistSn, Long trainId, Integer seatType,
                                String startStation, String endStation, String queueKey) {
        // DB 校验状态
        Waitlist waitlist = waitlistMapper.selectOne(
                Wrappers.lambdaQuery(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
        );
        if (waitlist == null || !WaitlistStatusEnum.WAITING.getCode().equals(waitlist.getStatus())) {
            // 脏数据，从 Redis 移除
            stringRedisTemplate.opsForZSet().remove(queueKey, waitlistSn);
            return true; // 跳过，继续下一个
        }

        // 检查是否过期
        if (waitlist.getExpireTime().isBefore(LocalDateTime.now())) {
            expireWaitlist(waitlist, queueKey);
            return true; // 已过期，继续下一个
        }

        // CAS: WAITING → MATCHED
        int updated = waitlistMapper.update(null,
                Wrappers.lambdaUpdate(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
                        .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                        .set(Waitlist::getStatus, WaitlistStatusEnum.MATCHED.getCode())
        );
        if (updated == 0) {
            // 被取消或已匹配，跳过
            stringRedisTemplate.opsForZSet().remove(queueKey, waitlistSn);
            return true;
        }

        // CAS 成功，尝试选座锁座
        try {
            // 获取乘车人信息
            List<WaitlistPassenger> wpList = waitlistPassengerMapper.selectList(
                    Wrappers.lambdaQuery(WaitlistPassenger.class)
                            .eq(WaitlistPassenger::getWaitlistId, waitlist.getId())
            );
            List<Long> passengerIds = wpList.stream().map(WaitlistPassenger::getPassengerId).toList();
            List<Passenger> passengers = passengerMapper.selectByIds(passengerIds);
            if (passengers.isEmpty()) {
                throw new ClientException("乘车人信息不存在");
            }

            // 构建 OrderCreatePassengerDetailDTO 列表给 SeatSelector
            List<com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO> passengerDTOs = new ArrayList<>();
            for (Passenger p : passengers) {
                com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO dto =
                        new com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO();
                dto.setPassengerId(String.valueOf(p.getId()));
                dto.setSeatType(seatType);
                passengerDTOs.add(dto);
            }

            // 选座锁座
            List<TicketDTO> ticketDTOs = seatSelector.selectAndLockSeats(
                    trainId, startStation, endStation, passengerDTOs, null);

            // 匹配成功，写 OrderItem/Ticket，更新 Order
            onMatchSuccess(waitlist, ticketDTOs, queueKey);
            return true;

        } catch (ClientException e) {
            // 余票不足等业务失败，回退状态为 WAITING，重新入队
            log.warn("候补匹配选座失败, waitlistSn={}, msg={}", waitlistSn, e.getMessage());
            waitlistMapper.update(null,
                    Wrappers.lambdaUpdate(Waitlist.class)
                            .eq(Waitlist::getWaitlistSn, waitlistSn)
                            .eq(Waitlist::getStatus, WaitlistStatusEnum.MATCHED.getCode())
                            .set(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
            );
            return false; // 余票不足，停止处理
        } catch (Exception e) {
            // 临时故障，回退状态
            log.error("候补匹配异常, waitlistSn={}", waitlistSn, e);
            waitlistMapper.update(null,
                    Wrappers.lambdaUpdate(Waitlist.class)
                            .eq(Waitlist::getWaitlistSn, waitlistSn)
                            .eq(Waitlist::getStatus, WaitlistStatusEnum.MATCHED.getCode())
                            .set(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
            );
            throw e; // 向上传播，触发 nack 重试（如果是 MQ 场景）
        }
    }

    /**
     * 匹配成功后的处理：写 OrderItem/Ticket，更新 Order，更新 Pay
     */
    private void onMatchSuccess(Waitlist waitlist, List<TicketDTO> ticketDTOs, String queueKey) {
        String orderSn = waitlist.getOrderSn();
        Long trainId = waitlist.getTrainId();

        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderSn, orderSn));
        String username = order != null ? order.getUsername() : "";

        // 构建 OrderItem 和 Ticket
        List<OrderItem> orderItems = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();
        for (TicketDTO ticketDTO : ticketDTOs) {
            orderItems.add(OrderItem.builder()
                    .orderSn(orderSn)
                    .phone(ticketDTO.getPhone())
                    .userId(waitlist.getUserId())
                    .username(username)
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
            tickets.add(Ticket.builder()
                    .orderSn(orderSn)
                    .username(username)
                    .trainId(trainId)
                    .carriageNumber(ticketDTO.getCarriageNumber())
                    .seatNumber(ticketDTO.getSeatNumber())
                    .passengerId(Long.parseLong(ticketDTO.getPassengerId()))
                    .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                    .build()
            );
        }

        // 写 OrderItem/Ticket
        for (OrderItem item : orderItems) {
            orderItemMapper.insert(item);
        }
        for (Ticket ticket : tickets) {
            ticketMapper.insert(ticket);
        }

        // 状态机: WAITLIST → UNPAID
        stateMachine.transition(orderSn,
                OrderStatusEnum.WAITLIST.getCode(), OrderStatusEnum.UNPAID.getCode(),
                OrderStateEvent.WAITLIST_MATCH, "SYSTEM");

        // Pay: FROZEN → SUCCESS (CAS)
        payMapper.update(null,
                Wrappers.lambdaUpdate(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
                        .eq(Pay::getStatus, PayStatusEnum.FROZEN.getCode())
                        .set(Pay::getStatus, PayStatusEnum.SUCCESS.getCode())
        );

        // ZREM Redis
        stringRedisTemplate.opsForZSet().remove(queueKey, waitlist.getWaitlistSn());

        // 发送超时消息
        sendOrderTimeoutMessage(orderSn);

        log.info("候补匹配成功, waitlistSn={}, orderSn={}", waitlist.getWaitlistSn(), orderSn);
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void cancelWaitlist(String waitlistSn) {
        Waitlist waitlist = waitlistMapper.selectOne(
                Wrappers.lambdaQuery(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
        );
        if (waitlist == null) {
            throw new ClientException("候补记录不存在");
        }

        Integer status = waitlist.getStatus();

        // 已取消/已过期 → 幂等
        if (WaitlistStatusEnum.CANCELED.getCode().equals(status)) {
            return;
        }
        if (WaitlistStatusEnum.EXPIRED.getCode().equals(status)) {
            throw new ClientException("候补已过期");
        }

        String orderSn = waitlist.getOrderSn();

        // status == WAITING: 尝试 CAS 取消
        if (WaitlistStatusEnum.WAITING.getCode().equals(status)) {
            int updated = waitlistMapper.update(null,
                    Wrappers.lambdaUpdate(Waitlist.class)
                            .eq(Waitlist::getWaitlistSn, waitlistSn)
                            .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                            .set(Waitlist::getStatus, WaitlistStatusEnum.CANCELED.getCode())
            );
            if (updated > 0) {
                // CAS 成功：清理 Redis + 退款
                String queueKey = buildQueueKey(waitlist.getTrainId(), waitlist.getSeatType(),
                        waitlist.getStartStation(), waitlist.getEndStation());
                stringRedisTemplate.opsForZSet().remove(queueKey, waitlistSn);

                // Order → CANCELED
                orderMapper.update(null,
                        Wrappers.lambdaUpdate(Order.class)
                                .eq(Order::getOrderSn, orderSn)
                                .eq(Order::getStatus, OrderStatusEnum.WAITLIST.getCode())
                                .set(Order::getStatus, OrderStatusEnum.CANCELED.getCode())
                );

                // Pay: FROZEN → REFUNDED
                payMapper.update(null,
                        Wrappers.lambdaUpdate(Pay.class)
                                .eq(Pay::getOrderSn, orderSn)
                                .eq(Pay::getStatus, PayStatusEnum.FROZEN.getCode())
                                .set(Pay::getStatus, PayStatusEnum.REFUNDED.getCode())
                );

                log.info("候补已取消(WAITING快速路径), waitlistSn={}", waitlistSn);
                return;
            }
            // CAS 失败：match 抢先了，fall through
        }

        // status == MATCHED 或 CAS 失败：降级调用 cancelOrder
        log.info("候补取消降级为cancelOrder, waitlistSn={}, orderSn={}", waitlistSn, orderSn);
        orderService.cancelOrder(orderSn);
    }

    @Override
    public WaitlistDetailRespDTO getWaitlistDetail(String waitlistSn) {
        Waitlist waitlist = waitlistMapper.selectOne(
                Wrappers.lambdaQuery(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
        );
        if (waitlist == null) {
            throw new ClientException("候补记录不存在");
        }

        // 查询队列位置
        Long queuePosition = -1L;
        if (WaitlistStatusEnum.WAITING.getCode().equals(waitlist.getStatus())) {
            String queueKey = buildQueueKey(waitlist.getTrainId(), waitlist.getSeatType(),
                    waitlist.getStartStation(), waitlist.getEndStation());
            Long rank = stringRedisTemplate.opsForZSet().rank(queueKey, waitlistSn);
            queuePosition = rank != null ? rank : -1L;
        }

        // 查询列车信息
        Train train = trainMapper.selectById(waitlist.getTrainId());

        // 查询乘客信息
        List<WaitlistPassenger> wpList = waitlistPassengerMapper.selectList(
                Wrappers.lambdaQuery(WaitlistPassenger.class)
                        .eq(WaitlistPassenger::getWaitlistId, waitlist.getId())
        );
        List<Long> passengerIds = wpList.stream().map(WaitlistPassenger::getPassengerId).toList();
        List<Passenger> passengers = passengerIds.isEmpty()
                ? Collections.emptyList()
                : passengerMapper.selectByIds(passengerIds);

        Map<Long, Passenger> passengerMap = new HashMap<>();
        for (Passenger p : passengers) {
            passengerMap.put(p.getId(), p);
        }

        List<WaitlistDetailRespDTO.PassengerInfo> passengerInfos = new ArrayList<>();
        for (WaitlistPassenger wp : wpList) {
            Passenger p = passengerMap.get(wp.getPassengerId());
            passengerInfos.add(WaitlistDetailRespDTO.PassengerInfo.builder()
                    .passengerId(wp.getPassengerId())
                    .realName(p != null ? p.getRealName() : null)
                    .seatPreference(wp.getSeatPreference())
                    .build());
        }

        return WaitlistDetailRespDTO.builder()
                .waitlistSn(waitlist.getWaitlistSn())
                .status(waitlist.getStatus())
                .queuePosition(queuePosition)
                .expireTime(waitlist.getExpireTime())
                .trainNumber(train != null ? train.getTrainNumber() : null)
                .startStation(waitlist.getStartStation())
                .endStation(waitlist.getEndStation())
                .seatType(waitlist.getSeatType())
                .passengers(passengerInfos)
                .build();
    }

    @Override
    public void triggerMatch(Long trainId, String startStation, String endStation) {
        WaitlistMatchMessageDTO msg = WaitlistMatchMessageDTO.builder()
                .trainId(trainId)
                .startStation(startStation)
                .endStation(endStation)
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.WAITLIST_MATCH_EXCHANGE,
                    RabbitMQConfig.WAITLIST_MATCH_ROUTING_KEY, msg);
            log.info("候补匹配消息已发送, trainId={}", trainId);
        } catch (Exception e) {
            log.error("发送候补匹配消息失败, trainId={}", trainId, e);
        }
    }

    @Override
    public void processMatch(Long trainId, String startStation, String endStation) {
        int[] seatTypes = {1, 2, 3, 4, 5, 6, 7};
        for (int seatType : seatTypes) {
            String queueKey = buildQueueKey(trainId, seatType, startStation, endStation);
            Long size = stringRedisTemplate.opsForZSet().zCard(queueKey);
            if (size != null && size > 0) {
                try {
                    processWaitlist(trainId, seatType, startStation, endStation);
                } catch (Exception e) {
                    log.error("候补匹配异常, trainId={}, seatType={}", trainId, seatType, e);
                }
            }
        }
    }

    @Override
    public void cleanUpWaitlistByOrderSn(String orderSn) {
        Waitlist waitlist = waitlistMapper.selectOne(
                Wrappers.lambdaQuery(Waitlist.class)
                        .eq(Waitlist::getOrderSn, orderSn)
        );
        if (waitlist == null) {
            return;
        }

        // ZREM Redis
        String queueKey = buildQueueKey(waitlist.getTrainId(), waitlist.getSeatType(),
                waitlist.getStartStation(), waitlist.getEndStation());
        stringRedisTemplate.opsForZSet().remove(queueKey, waitlist.getWaitlistSn());

        // 更新 Waitlist 状态为 CANCELED（如果还是 WAITING/MATCHED）
        waitlistMapper.update(null,
                Wrappers.lambdaUpdate(Waitlist.class)
                        .eq(Waitlist::getOrderSn, orderSn)
                        .in(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode(), WaitlistStatusEnum.MATCHED.getCode())
                        .set(Waitlist::getStatus, WaitlistStatusEnum.CANCELED.getCode())
        );

        // Pay: FROZEN → REFUNDED
        payMapper.update(null,
                Wrappers.lambdaUpdate(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
                        .eq(Pay::getStatus, PayStatusEnum.FROZEN.getCode())
                        .set(Pay::getStatus, PayStatusEnum.REFUNDED.getCode())
        );

        log.info("候补清理完成(WAITLIST快速路径), orderSn={}", orderSn);
    }

    private void expireWaitlist(Waitlist waitlist, String queueKey) {
        String waitlistSn = waitlist.getWaitlistSn();
        String orderSn = waitlist.getOrderSn();

        // Waitlist: WAITING → EXPIRED
        waitlistMapper.update(null,
                Wrappers.lambdaUpdate(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
                        .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                        .set(Waitlist::getStatus, WaitlistStatusEnum.EXPIRED.getCode())
        );

        // Order → CANCELED
        orderMapper.update(null,
                Wrappers.lambdaUpdate(Order.class)
                        .eq(Order::getOrderSn, orderSn)
                        .eq(Order::getStatus, OrderStatusEnum.WAITLIST.getCode())
                        .set(Order::getStatus, OrderStatusEnum.CANCELED.getCode())
        );

        // Pay: FROZEN → REFUNDED
        payMapper.update(null,
                Wrappers.lambdaUpdate(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
                        .eq(Pay::getStatus, PayStatusEnum.FROZEN.getCode())
                        .set(Pay::getStatus, PayStatusEnum.REFUNDED.getCode())
        );

        // ZREM Redis
        stringRedisTemplate.opsForZSet().remove(queueKey, waitlistSn);

        log.info("候补已过期, waitlistSn={}", waitlistSn);
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
            log.info("候补兑现超时取消消息已发送, orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("发送超时取消消息失败, orderSn={}", orderSn, e);
        }
    }

    private String buildQueueKey(Long trainId, Integer seatType, String startStation, String endStation) {
        return String.format(RedisConstant.WAITLIST_QUEUE, trainId, seatType, startStation, endStation);
    }

    private BigDecimal getPrice(Long trainId, String startStation, String endStation, Integer seatType) {
        TrainStationPrice priceRecord = trainStationPriceMapper.selectOne(
                Wrappers.lambdaQuery(TrainStationPrice.class)
                        .eq(TrainStationPrice::getTrainId, trainId)
                        .eq(TrainStationPrice::getStartStation, startStation)
                        .eq(TrainStationPrice::getEndStation, endStation)
                        .eq(TrainStationPrice::getSeatType, seatType)
        );
        return priceRecord != null ? new BigDecimal(priceRecord.getPrice()) : BigDecimal.ZERO;
    }
}
