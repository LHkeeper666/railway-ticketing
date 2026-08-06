package com.lhkeeper.ticketing.railway_ticketing.service.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Pay;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Waitlist;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.WaitlistStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PayMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.WaitlistMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistExpireTask {

    private final WaitlistMapper waitlistMapper;
    private final OrderMapper orderMapper;
    private final PayMapper payMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    @Scheduled(fixedDelay = 60_000)
    public void expireWaitingOrders() {
        DistributedLock lock = lockFactory.tryLock("task:waitlist:expire", 55);
        if (lock == null) {
            return;
        }
        try {
            List<Waitlist> expiredList = waitlistMapper.selectList(
                    Wrappers.lambdaQuery(Waitlist.class)
                            .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                            .lt(Waitlist::getExpireTime, LocalDateTime.now())
                            .last("LIMIT 100")
            );

            for (Waitlist w : expiredList) {
                try {
                    expireOne(w);
                } catch (Exception e) {
                    log.error("候补过期处理异常, waitlistSn={}", w.getWaitlistSn(), e);
                }
            }

            if (!expiredList.isEmpty()) {
                log.info("候补过期清理完成, count={}", expiredList.size());
            }
        } finally {
            lock.unlock();
        }
    }

    private void expireOne(Waitlist w) {
        String waitlistSn = w.getWaitlistSn();
        String orderSn = w.getOrderSn();

        // CAS: WAITING → EXPIRED
        int updated = waitlistMapper.update(null,
                Wrappers.lambdaUpdate(Waitlist.class)
                        .eq(Waitlist::getWaitlistSn, waitlistSn)
                        .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                        .set(Waitlist::getStatus, WaitlistStatusEnum.EXPIRED.getCode())
        );
        if (updated == 0) {
            return; // 已被处理
        }

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
        String queueKey = String.format(RedisConstant.WAITLIST_QUEUE,
                w.getTrainId(), w.getSeatType(), w.getStartStation(), w.getEndStation());
        stringRedisTemplate.opsForZSet().remove(queueKey, waitlistSn);

        log.info("候补已过期, waitlistSn={}", waitlistSn);
    }
}
