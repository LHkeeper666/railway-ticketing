package com.lhkeeper.ticketing.railway_ticketing.service.handler.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Pay;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PayMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底退款定时任务，扫描 PENDING_REFUND 状态的支付记录并执行退款。
 * 处理"支付回调到达时订单已被取消"的场景。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundPendingTask {

    private final PayMapper payMapper;
    private final OrderMapper orderMapper;
    private final DistributedLockFactory lockFactory;

    private static final int BATCH_SIZE = 100;
    private static final long LOCK_TTL = 60;

    @Scheduled(fixedDelay = 60_000)
    public void process() {
        DistributedLock lock = lockFactory.tryLock(
                RedisConstant.LOCK_KEY_PREFIX + "refund-pending", LOCK_TTL);
        if (lock == null) {
            return;
        }
        try {
            int total = 0;
            while (true) {
                List<Pay> pendingRefunds = payMapper.selectList(
                        Wrappers.lambdaQuery(Pay.class)
                                .eq(Pay::getStatus, "PENDING_REFUND")
                                .last("LIMIT " + BATCH_SIZE)
                );
                if (pendingRefunds.isEmpty()) {
                    break;
                }
                for (Pay pay : pendingRefunds) {
                    try {
                        processOne(pay);
                        total++;
                    } catch (Exception e) {
                        log.error("退款处理失败, paySn={}, orderSn={}", pay.getPaySn(), pay.getOrderSn(), e);
                    }
                }
            }
            if (total > 0) {
                log.info("退款兜底处理完成, count={}", total);
            }
        } finally {
            lock.unlock();
        }
    }

    private void processOne(Pay pay) {
        // 防御性检查：确认订单确实是 CANCELED
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, pay.getOrderSn())
        );
        if (order == null) {
            log.warn("退款-订单不存在, paySn={}, orderSn={}", pay.getPaySn(), pay.getOrderSn());
            return;
        }
        if (!OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            log.warn("退款-订单状态非 CANCELED，跳过, paySn={}, orderSn={}, status={}",
                    pay.getPaySn(), pay.getOrderSn(), order.getStatus());
            return;
        }

        // 模拟退款（本项目无真实支付网关）
        log.info("模拟退款, paySn={}, orderSn={}, amount={}, channel={}, tradeNo={}",
                pay.getPaySn(), pay.getOrderSn(), pay.getTotalAmount(),
                pay.getChannel(), pay.getTradeNo());

        // CAS: PENDING_REFUND → REFUNDED
        int updated = payMapper.update(null,
                Wrappers.lambdaUpdate(Pay.class)
                        .eq(Pay::getPaySn, pay.getPaySn())
                        .eq(Pay::getStatus, "PENDING_REFUND")
                        .set(Pay::getStatus, "REFUNDED")
        );
        if (updated > 0) {
            log.info("退款完成, paySn={}, orderSn={}", pay.getPaySn(), pay.getOrderSn());
        } else {
            log.info("退款 CAS 失败（已被其他实例处理）, paySn={}", pay.getPaySn());
        }
    }
}
