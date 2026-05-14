package com.lhkeeper.ticketing.railway_ticketing.service.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底清理超时 PENDING 订单，防止消息丢失导致订单永久挂起
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderCleanupTask {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    private static final int BATCH_SIZE = 100;
    private static final int TIMEOUT_MINUTES = 15;
    private static final long LOCK_TTL = 60;

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        DistributedLock lock = lockFactory.tryLock("pending-cleanup", LOCK_TTL);
        if (lock == null) return;
        try {
            int total = 0;
            while (true) {
                List<Order> orders = scanStalePendingOrders();
                if (orders.isEmpty()) break;
                for (Order order : orders) {
                    try {
                        orderService.cancelOrder(order.getOrderSn(), true);
                        total++;
                    } catch (Exception e) {
                        log.error("兜底取消失败, orderSn={}", order.getOrderSn(), e);
                    }
                }
            }
            if (total > 0) log.info("兜底清理完成, count={}", total);
        } finally {
            lock.unlock();
        }
    }

    private List<Order> scanStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        return orderMapper.selectList(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getStatus, OrderStatusEnum.PENDING.getCode())
                        .lt(Order::getCreateTime, cutoff)
                        .last("LIMIT " + BATCH_SIZE)
        );
    }

}
