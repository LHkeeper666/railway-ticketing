package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangeReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.ChangeRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.ChangeService;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.select.SeatSelector;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.RefundChangeFeeCalculator;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 改签服务实现：跨车次/同车次改签，支持变更座位类型和站点。
 * 手续费参考 12306 阶梯规则：>48h 免费，24-48h 5%，<24h 15%，开车后不可改签。
 * 改签须选择全部车票（整单改签），新票直接置 PAID，价差>0 需补付。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeServiceImpl implements ChangeService {

    private final ChangeOrderMapper changeOrderMapper;
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final OrderItemMapper orderItemMapper;
    private final PayMapper payMapper;
    private final SeatMapper seatMapper;
    private final SeatSelector seatSelector;
    private final SnowflakeUtil snowflakeUtil;
    private final TrainStationService trainStationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;
    private final AbstractChainContext<ChangeReqDTO> changeChainContext;
    @Lazy
    @Autowired
    private WaitlistService waitlistService;
    private final TrainMapper trainMapper;
    private final TicketServiceImpl ticketServiceImpl;
    private final OrderItemServiceImpl orderItemServiceImpl;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public ChangeRespDTO change(ChangeReqDTO reqDTO) {
        changeChainContext.handler(ChainMarkEnum.ORDER_CHANGE.name(), reqDTO);

        String orderSn = reqDTO.getOrderSn();
        Long newTrainId = Long.parseLong(reqDTO.getNewTrainId());
        List<Long> ticketIds = reqDTO.getTicketIds();
        log.info("开始改签, orderSn={}, ticketCount={}, newTrainId={}", orderSn, ticketIds.size(), newTrainId);

        // 查订单
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );

        // 要求整单改签
        Long totalPaidTickets = ticketMapper.selectCount(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
                        .eq(Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
        );
        if (ticketIds.size() != totalPaidTickets) {
            throw new ClientException("改签须选择全部车票");
        }

        // 查旧 ticket
        List<Ticket> oldTickets = ticketMapper.selectBatchIds(ticketIds);
        if (oldTickets.size() != ticketIds.size()) {
            throw new ClientException("部分车票不存在");
        }
        for (Ticket ticket : oldTickets) {
            if (!ticket.getOrderSn().equals(orderSn)) {
                throw new ClientException("车票不属于此订单");
            }
            if (!TicketStatusEnum.PAID.getCode().equals(ticket.getTicketStatus())) {
                throw new ClientException("车票状态不允许改签: " + ticket.getId());
            }
        }

        // 查旧 OrderItem
        List<OrderItem> oldOrderItems = orderItemMapper.selectList(
                Wrappers.lambdaQuery(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
        );
        Map<String, OrderItem> oldItemMap = new HashMap<>();
        for (OrderItem item : oldOrderItems) {
            oldItemMap.put(item.getCarriageNumber() + "_" + item.getSeatNumber(), item);
        }

        // -------- 保存改签前的旧值（Order 后续会被更新）--------
        Long oldTrainId = order.getTrainId();
        String oldStartStation = order.getStartStation();
        String oldEndStation = order.getEndStation();
        LocalDateTime oldDepartureTime = order.getDepartureTime();

        // 确定座位类型
        Integer seatType = reqDTO.getNewSeatType();
        if (seatType == null) {
            seatType = oldOrderItems.get(0).getSeatType();
        }

        // 新旧列车按 trainId 升序获取分布式锁，防死锁
        Long lockFirst = Math.min(oldTrainId, newTrainId);
        Long lockSecond = Math.max(oldTrainId, newTrainId);

        DistributedLock lock1 = null, lock2 = null;
        try {
            lock1 = acquireLock(lockFirst, seatType);
            if (!lockFirst.equals(lockSecond)) {
                lock2 = acquireLock(lockSecond, seatType);
            }

            // 构建乘客列表（从旧 Ticket 提取 passengerId）
            List<OrderCreatePassengerDetailDTO> passengers = new ArrayList<>();
            for (Ticket ticket : oldTickets) {
                passengers.add(OrderCreatePassengerDetailDTO.builder()
                        .passengerId(String.valueOf(ticket.getPassengerId()))
                        .seatType(seatType)
                        .build());
            }

            // 选座并锁定新座位
            List<TicketDTO> newTicketDTOs = seatSelector.selectAndLockSeats(
                    newTrainId, reqDTO.getNewStartStation(), reqDTO.getNewEndStation(),
                    passengers, reqDTO.getChooseSeats());

            // 计算手续费（基于旧车次出发时间）
            LocalDateTime now = LocalDateTime.now();
            BigDecimal oldTotalAmount = BigDecimal.ZERO;
            for (OrderItem item : oldOrderItems) {
                oldTotalAmount = oldTotalAmount.add(item.getAmount());
            }
            BigDecimal fee;
            try {
                fee = RefundChangeFeeCalculator.calculateChangeFee(oldTotalAmount, oldDepartureTime, now);
            } catch (IllegalArgumentException e) {
                throw new ClientException(e.getMessage());
            }

            // 计算新旧票价差
            BigDecimal newTotalAmount = BigDecimal.ZERO;
            for (TicketDTO dto : newTicketDTOs) {
                newTotalAmount = newTotalAmount.add(dto.getAmount());
            }
            BigDecimal priceDiff = newTotalAmount.subtract(oldTotalAmount);

            // CAS: 逐张旧 ticket PAID → CHANGED
            for (Ticket oldTicket : oldTickets) {
                int updated = ticketMapper.update(null,
                        Wrappers.lambdaUpdate(Ticket.class)
                                .eq(Ticket::getId, oldTicket.getId())
                                .eq(Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
                                .set(Ticket::getTicketStatus, TicketStatusEnum.CHANGED.getCode())
                );
                if (updated == 0) {
                    throw new ClientException("车票已被处理，请刷新重试");
                }
            }

            // 释放旧座位
            List<TrainStation> oldTrainStations = trainStationService.getTrainStationsByTrainId(oldTrainId);
            for (Ticket oldTicket : oldTickets) {
                Long purchaseMask = oldTicket.getPurchaseMask();
                if (purchaseMask == null) {
                    purchaseMask = StationCalculateUtil.bitmapMask(
                            oldTrainStations, oldStartStation, oldEndStation);
                }
                seatMapper.update(null,
                        Wrappers.lambdaUpdate(Seat.class)
                                .eq(Seat::getTrainId, oldTicket.getTrainId())
                                .eq(Seat::getCarriageNumber, oldTicket.getCarriageNumber())
                                .eq(Seat::getSeatNumber, oldTicket.getSeatNumber())
                                .apply("(seat_bitmap & {0}) = {0}", purchaseMask)
                                .setSql("seat_bitmap = seat_bitmap & ~" + purchaseMask)
                );
            }

            // 标记旧 OrderItem → CHANGED
            for (Ticket oldTicket : oldTickets) {
                orderItemMapper.update(null,
                        Wrappers.lambdaUpdate(OrderItem.class)
                                .eq(OrderItem::getOrderSn, orderSn)
                                .eq(OrderItem::getCarriageNumber, oldTicket.getCarriageNumber())
                                .eq(OrderItem::getSeatNumber, oldTicket.getSeatNumber())
                                .set(OrderItem::getStatus, TicketStatusEnum.CHANGED.getCode())
                );
            }

            // 创建新 Ticket + 新 OrderItem
            List<Long> newTicketIds = new ArrayList<>();
            List<TrainStation> newTrainStations = trainStationService.getTrainStationsByTrainId(newTrainId);

            for (int i = 0; i < newTicketDTOs.size(); i++) {
                TicketDTO dto = newTicketDTOs.get(i);
                Ticket oldTicket = oldTickets.get(i);
                String key = oldTicket.getCarriageNumber() + "_" + oldTicket.getSeatNumber();
                OrderItem oldItem = oldItemMap.get(key);

                // 创建新 OrderItem（复用旧的乘客信息）
                OrderItem newItem = OrderItem.builder()
                        .orderSn(orderSn)
                        .userId(order.getUserId())
                        .username(order.getUsername())
                        .trainId(newTrainId)
                        .carriageNumber(dto.getCarriageNumber())
                        .seatType(seatType)
                        .seatNumber(dto.getSeatNumber())
                        .realName(oldItem.getRealName())
                        .idType(oldItem.getIdType())
                        .idCard(oldItem.getIdCard())
                        .ticketType(oldItem.getTicketType())
                        .phone(oldItem.getPhone())
                        .status(TicketStatusEnum.PAID.getCode())
                        .amount(dto.getAmount())
                        .build();
                orderItemServiceImpl.save(newItem);

                // 创建新 Ticket
                Ticket newTicket = Ticket.builder()
                        .orderSn(orderSn)
                        .username(order.getUsername())
                        .trainId(newTrainId)
                        .carriageNumber(dto.getCarriageNumber())
                        .seatNumber(dto.getSeatNumber())
                        .passengerId(oldTicket.getPassengerId())
                        .ticketStatus(TicketStatusEnum.PAID.getCode())
                        .purchaseMask(dto.getPurchaseMask())
                        .build();
                ticketServiceImpl.save(newTicket);
                newTicketIds.add(newTicket.getId());
            }

            // 获取新车次信息并更新 Order
            Train newTrain = getTrainInfo(newTrainId);
            order.setTrainId(newTrainId);
            order.setStartStation(reqDTO.getNewStartStation());
            order.setEndStation(reqDTO.getNewEndStation());
            order.setTrainNumber(newTrain.getTrainNumber());
            // 获取新列车的发车/到达时间
            for (TrainStation ts : newTrainStations) {
                if (reqDTO.getNewStartStation().equals(ts.getStartStation())) {
                    order.setDepartureTime(ts.getDepartureTime());
                }
                if (reqDTO.getNewEndStation().equals(ts.getStartStation())) {
                    order.setArrivalTime(ts.getArrivalTime());
                }
            }
            orderMapper.updateById(order);

            // 创建改签记录
            String changeSn = String.valueOf(snowflakeUtil.generateId());
            String diffStatus = priceDiff.compareTo(BigDecimal.ZERO) > 0 ? "PENDING_PAY" : "COMPLETED";
            ChangeOrder changeOrder = ChangeOrder.builder()
                    .changeSn(changeSn)
                    .orderSn(orderSn)
                    .oldTrainId(oldTrainId)
                    .newTrainId(newTrainId)
                    .oldStartStation(oldStartStation)
                    .oldEndStation(oldEndStation)
                    .newStartStation(reqDTO.getNewStartStation())
                    .newEndStation(reqDTO.getNewEndStation())
                    .oldAmount(oldTotalAmount)
                    .newAmount(newTotalAmount)
                    .priceDiff(priceDiff)
                    .feeAmount(fee)
                    .changeTicketCount(oldTickets.size())
                    .status(diffStatus)
                    .oldDepartureTime(oldDepartureTime)
                    .newDepartureTime(order.getDepartureTime())
                    .build();
            changeOrderMapper.insert(changeOrder);

            // 价差处理：差价>0 创建补差 Pay
            if (priceDiff.compareTo(BigDecimal.ZERO) > 0) {
                Pay diffPay = Pay.builder()
                        .paySn(String.valueOf(snowflakeUtil.generateId()))
                        .orderSn(orderSn)
                        .totalAmount(priceDiff.add(fee))
                        .status(PayStatusEnum.PENDING.getCode())
                        .build();
                payMapper.insert(diffPay);
                log.info("改签需补差价, orderSn={}, priceDiff={}, fee={}", orderSn, priceDiff, fee);
            } else if (priceDiff.compareTo(BigDecimal.ZERO) < 0) {
                log.info("改签需退差价, orderSn={}, refundAmount={}", orderSn, priceDiff.negate());
            }

            // 失效双方缓存
            invalidateCache(oldTrainId, oldStartStation, oldEndStation, oldTrainStations);
            invalidateCache(newTrainId, reqDTO.getNewStartStation(), reqDTO.getNewEndStation(), newTrainStations);

            // 触发候补匹配（双方列车）
            String oldStart = oldStartStation;
            String oldEnd = oldEndStation;
            String newStart = reqDTO.getNewStartStation();
            String newEnd = reqDTO.getNewEndStation();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                waitlistService.triggerMatch(oldTrainId, oldStart, oldEnd);
                                waitlistService.triggerMatch(newTrainId, newStart, newEnd);
                            } catch (Exception e) {
                                log.error("改签后触发候补匹配异常", e);
                            }
                        }
                    });

            log.info("改签完成, changeSn={}, orderSn={}, oldTrain={}, newTrain={}, priceDiff={}",
                    changeSn, orderSn, oldTrainId, newTrainId, priceDiff);

            return ChangeRespDTO.builder()
                    .changeSn(changeSn)
                    .orderSn(orderSn)
                    .priceDiff(priceDiff)
                    .feeAmount(fee)
                    .changeTicketCount(oldTickets.size())
                    .status(diffStatus)
                    .newTicketIds(newTicketIds)
                    .build();

        } finally {
            if (lock2 != null) lock2.unlock();
            if (lock1 != null) lock1.unlock();
        }
    }

    private DistributedLock acquireLock(Long trainId, Integer seatType) {
        String lockKey = "seat:train:" + trainId + ":type:" + seatType;
        DistributedLock lock = lockFactory.tryLock(lockKey, 10);
        if (lock == null) {
            throw new ClientException("系统繁忙，请稍后重试");
        }
        return lock;
    }

    private void invalidateCache(Long trainId, String startStation, String endStation,
                                  List<TrainStation> trainStations) {
        List<RouteDTO> routes = StationCalculateUtil.takeoutStation(trainStations, startStation, endStation);
        String trainIdStr = String.valueOf(trainId);
        for (RouteDTO route : routes) {
            String key = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    trainIdStr, route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(key);
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
}
