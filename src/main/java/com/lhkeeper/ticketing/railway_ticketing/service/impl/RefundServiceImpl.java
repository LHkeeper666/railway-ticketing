package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.RouteDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.RefundRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStateEvent;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.statemachine.OrderStateMachine;
import com.lhkeeper.ticketing.railway_ticketing.domain.statemachine.TransitResult;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.RefundService;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.RefundChangeFeeCalculator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 退票服务实现，支持部分退票（按 ticket 粒度）。
 * 手续费参考 12306 阶梯规则：>48h 免费，24-48h 10%，<24h 20%，开车后不可退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundOrderMapper refundOrderMapper;
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final OrderItemMapper orderItemMapper;
    private final PayMapper payMapper;
    private final SeatMapper seatMapper;
    private final SnowflakeUtil snowflakeUtil;
    private final TrainStationService trainStationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AbstractChainContext<RefundReqDTO> refundChainContext;
    private final OrderStateMachine stateMachine;
    @Lazy
    @Autowired
    private WaitlistService waitlistService;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public RefundRespDTO refund(RefundReqDTO reqDTO) {
        refundChainContext.handler(ChainMarkEnum.ORDER_REFUND.name(), reqDTO);

        String orderSn = reqDTO.getOrderSn();
        List<Long> ticketIds = reqDTO.getTicketIds();
        log.info("开始退票, orderSn={}, ticketCount={}", orderSn, ticketIds.size());

        // 查订单（快照读，链校验已确认 PAID）
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );

        // 查要退的 ticket
        List<Ticket> tickets = ticketMapper.selectBatchIds(ticketIds);
        if (tickets.size() != ticketIds.size()) {
            throw new ClientException("部分车票不存在");
        }
        for (Ticket ticket : tickets) {
            if (!ticket.getOrderSn().equals(orderSn)) {
                throw new ClientException("车票不属于此订单");
            }
            if (!TicketStatusEnum.PAID.getCode().equals(ticket.getTicketStatus())) {
                throw new ClientException("车票状态不允许退票: " + ticket.getId());
            }
        }

        // 构建 OrderItem 查找表（carriageNumber_seatNumber → OrderItem）
        List<OrderItem> orderItems = orderItemMapper.selectList(
                Wrappers.lambdaQuery(OrderItem.class)
                        .eq(OrderItem::getOrderSn, orderSn)
        );
        Map<String, OrderItem> itemMap = new HashMap<>();
        for (OrderItem item : orderItems) {
            itemMap.put(item.getCarriageNumber() + "_" + item.getSeatNumber(), item);
        }

        // 计算手续费 & 校验退票时间
        LocalDateTime now = LocalDateTime.now();
        BigDecimal totalTicketAmount = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;

        for (Ticket ticket : tickets) {
            String key = ticket.getCarriageNumber() + "_" + ticket.getSeatNumber();
            OrderItem item = itemMap.get(key);
            if (item == null) {
                throw new ServiceException("订单项不存在");
            }
            BigDecimal ticketPrice = item.getAmount();
            BigDecimal fee;
            try {
                fee = RefundChangeFeeCalculator.calculateRefundFee(ticketPrice, order.getDepartureTime(), now);
            } catch (IllegalArgumentException e) {
                throw new ClientException(e.getMessage());
            }
            totalTicketAmount = totalTicketAmount.add(ticketPrice);
            totalFee = totalFee.add(fee);
        }
        BigDecimal refundAmount = totalTicketAmount.subtract(totalFee);

        // 创建退款单
        String refundSn = String.valueOf(snowflakeUtil.generateId());
        RefundOrder refundOrder = RefundOrder.builder()
                .refundSn(refundSn)
                .orderSn(orderSn)
                .refundAmount(refundAmount)
                .feeAmount(totalFee)
                .totalAmount(totalTicketAmount)
                .refundTicketCount(tickets.size())
                .status(PayStatusEnum.SUCCESS.getCode())
                .reason(reqDTO.getReason())
                .departureTime(order.getDepartureTime())
                .build();
        refundOrderMapper.insert(refundOrder);

        // CAS: 逐张 ticket PAID → REFUNDED，释放座位，更新 OrderItem
        List<TrainStation> trainStations = trainStationService.getTrainStationsByTrainId(order.getTrainId());
        for (Ticket ticket : tickets) {
            int updated = ticketMapper.update(null,
                    Wrappers.lambdaUpdate(Ticket.class)
                            .eq(Ticket::getId, ticket.getId())
                            .eq(Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
                            .set(Ticket::getTicketStatus, TicketStatusEnum.REFUNDED.getCode())
            );
            if (updated == 0) {
                throw new ClientException("车票已被处理，请刷新重试");
            }

            // 释放座位（使用 ticket 落盘时的 purchaseMask）
            Long purchaseMask = ticket.getPurchaseMask();
            if (purchaseMask == null) {
                purchaseMask = StationCalculateUtil.bitmapMask(
                        trainStations, order.getStartStation(), order.getEndStation());
            }
            seatMapper.update(null,
                    Wrappers.lambdaUpdate(Seat.class)
                            .eq(Seat::getTrainId, ticket.getTrainId())
                            .eq(Seat::getCarriageNumber, ticket.getCarriageNumber())
                            .eq(Seat::getSeatNumber, ticket.getSeatNumber())
                            .apply("(seat_bitmap & {0}) = {0}", purchaseMask)
                            .setSql("seat_bitmap = seat_bitmap & ~" + purchaseMask)
            );

            // 更新对应 OrderItem
            orderItemMapper.update(null,
                    Wrappers.lambdaUpdate(OrderItem.class)
                            .eq(OrderItem::getOrderSn, orderSn)
                            .eq(OrderItem::getCarriageNumber, ticket.getCarriageNumber())
                            .eq(OrderItem::getSeatNumber, ticket.getSeatNumber())
                            .set(OrderItem::getStatus, TicketStatusEnum.REFUNDED.getCode())
            );
        }

        // 检查是否全部退票 → 整单变为 CANCELED
        Long remainingPaid = ticketMapper.selectCount(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getOrderSn, orderSn)
                        .eq(Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
        );
        if (remainingPaid == 0) {
            TransitResult r = stateMachine.transition(orderSn,
                    OrderStatusEnum.PAID.getCode(), OrderStatusEnum.CANCELED.getCode(),
                    OrderStateEvent.REFUND_ALL,
                    String.valueOf(UserContext.get().getUserId()));
            if (r.isSuccess()) {
                payMapper.update(null,
                        Wrappers.lambdaUpdate(Pay.class)
                                .eq(Pay::getOrderSn, orderSn)
                                .set(Pay::getStatus, PayStatusEnum.REFUNDED.getCode())
                );
            }
        }

        // 失效全部重叠区间的余票缓存
        List<RouteDTO> takeoutRoutes = StationCalculateUtil.takeoutStation(
                trainStations, order.getStartStation(), order.getEndStation());
        for (RouteDTO route : takeoutRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    order.getTrainId(), route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }

        // 触发候补匹配（事务提交后）
        Long triggerTrainId = order.getTrainId();
        String triggerStart = order.getStartStation();
        String triggerEnd = order.getEndStation();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            waitlistService.triggerMatch(triggerTrainId, triggerStart, triggerEnd);
                        } catch (Exception e) {
                            log.error("退票后触发候补匹配异常, trainId={}", triggerTrainId, e);
                        }
                    }
                });

        log.info("退票完成, refundSn={}, orderSn={}, ticketCount={}, refundAmount={}, fee={}",
                refundSn, orderSn, tickets.size(), refundAmount, totalFee);

        return RefundRespDTO.builder()
                .refundSn(refundSn)
                .orderSn(orderSn)
                .refundAmount(refundAmount)
                .feeAmount(totalFee)
                .refundTicketCount(tickets.size())
                .status(PayStatusEnum.SUCCESS.getCode())
                .build();
    }

}
