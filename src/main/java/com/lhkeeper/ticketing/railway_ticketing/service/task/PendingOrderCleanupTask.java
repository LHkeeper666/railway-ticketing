package com.lhkeeper.ticketing.railway_ticketing.service.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private static final String LOCK_KEY = RedisConstant.LOCK_KEY_PREFIX + "pending-cleanup";
    private static final long LOCK_TTL = 60;
    private static final int BATCH_SIZE = 100;
    private static final int TIMEOUT_MINUTES = 15;

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        if (!acquireLock()) return;
        try {
            int total = 0;
            while (true) {
                List<Order> orders = scanStalePendingOrders();
                if (orders.isEmpty()) break;
                for (Order order : orders) {
                    try {
                        orderService.cancelOrder(order.getOrderSn());
                        total++;
                    } catch (Exception e) {
                        log.error("兜底取消失败, orderSn={}", order.getOrderSn(), e);
                    }
                }
            }
            if (total > 0) log.info("兜底清理完成, count={}", total);
        } finally {
            releaseLock();
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

    private boolean acquireLock() {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue()
                        .setIfAbsent(LOCK_KEY, "1", LOCK_TTL, TimeUnit.SECONDS));
    }

    private void releaseLock() {
        stringRedisTemplate.delete(LOCK_KEY);
    }
}
