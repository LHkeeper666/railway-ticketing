package com.lhkeeper.ticketing.railway_ticketing.domain.statemachine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderStateLog;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStateEvent;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderStateLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单状态机。负责转移合法性校验、CAS 原子更新、审计日志记录、MQ 事件发布。
 * 只管理 Order.status 字段，不处理 OrderItem / Ticket / Pay 等关联表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderMapper orderMapper;
    private final OrderStateLogMapper auditLogMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 执行状态转移。
     *
     * @param orderSn      订单号
     * @param expectedFrom 期望的当前状态（CAS 条件）
     * @param targetTo     目标状态
     * @param event        触发事件
     * @param operator     操作人标识（userId 或 "SYSTEM"）
     * @return TransitResult，success=true 表示 CAS 成功，conflict=true 表示并发冲突
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Throwable.class)
    public TransitResult transition(String orderSn, Integer expectedFrom, Integer targetTo,
                                     OrderStateEvent event, String operator) {
        // 1. 合法性校验
        OrderStatusEnum fromState = OrderStatusEnum.fromCode(expectedFrom);
        if (fromState == null || !fromState.canTransitTo(targetTo, event)) {
            String err = String.format("非法状态转移: %s -> %d (event=%s)",
                    fromState != null ? fromState.name() : expectedFrom, targetTo, event.name());
            throw new ClientException(err);
        }

        // 2. CAS 原子更新
        int updated = orderMapper.update(null,
                Wrappers.lambdaUpdate(Order.class)
                        .eq(Order::getOrderSn, orderSn)
                        .eq(Order::getStatus, expectedFrom)
                        .set(Order::getStatus, targetTo)
        );

        // 3. 读取当前实际状态
        Order current = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        Integer actualStatus = current != null ? current.getStatus() : null;

        if (updated > 0) {
            // 4a. 审计日志（同事务）
            insertAuditLog(orderSn, expectedFrom, targetTo, event, operator);
            // 5a. MQ 事件（事务提交后）
            publishEventAfterCommit(orderSn, expectedFrom, targetTo, event);
            return TransitResult.success(actualStatus);
        }

        // 4b. CAS 冲突：不写审计，不发 MQ
        log.info("状态转移 CAS 冲突, orderSn={}, from={}, to={}, event={}, actualStatus={}",
                orderSn, expectedFrom, targetTo, event.name(), actualStatus);
        return TransitResult.conflict(actualStatus);
    }

    private void insertAuditLog(String orderSn, Integer fromStatus, Integer toStatus,
                                 OrderStateEvent event, String operator) {
        OrderStateLog auditLog = OrderStateLog.builder()
                .orderSn(orderSn)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .event(event.name())
                .operator(operator)
                .build();
        auditLogMapper.insert(auditLog);
    }

    private void publishEventAfterCommit(String orderSn, Integer fromStatus, Integer toStatus,
                                          OrderStateEvent event) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            Map<String, Object> body = new LinkedHashMap<>();
                            body.put("orderSn", orderSn);
                            body.put("fromStatus", fromStatus);
                            body.put("toStatus", toStatus);
                            body.put("event", event.name());
                            body.put("timestamp", System.currentTimeMillis());
                            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_STATUS_EXCHANGE,
                                    RabbitMQConfig.ORDER_STATUS_ROUTING_KEY, body);
                            log.info("订单状态事件已发布, orderSn={}, event={}", orderSn, event.name());
                        } catch (Exception e) {
                            log.error("发布订单状态事件失败, orderSn={}, event={}", orderSn, event.name(), e);
                        }
                    }
                });
    }
}
