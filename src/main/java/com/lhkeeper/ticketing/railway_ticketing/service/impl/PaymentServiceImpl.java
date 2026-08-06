package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.PayCreateRequest;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayCreateResult;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayInfoDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Pay;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderItemMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PayMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.PaymentService;
import com.lhkeeper.ticketing.railway_ticketing.service.PaymentStrategy;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付服务门面实现，负责渠道路由、Pay 表操作、CAS 状态更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final List<PaymentStrategy> strategies;
    private final PayMapper payMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final TicketMapper ticketMapper;
    private final SnowflakeUtil snowflakeUtil;
    private final AbstractChainContext<PayCallbackReqDTO> payNotifyChainContext;

    private Map<String, PaymentStrategy> strategyMap;

    private Map<String, PaymentStrategy> getStrategyMap() {
        if (strategyMap == null) {
            strategyMap = strategies.stream()
                    .collect(Collectors.toMap(PaymentStrategy::getChannel, Function.identity()));
        }
        return strategyMap;
    }

    private PaymentStrategy getStrategy(String channel) {
        PaymentStrategy strategy = getStrategyMap().get(channel);
        if (strategy == null) {
            throw new ClientException("不支持的支付渠道: " + channel);
        }
        return strategy;
    }

    @Override
    public PayCreateResult createPayment(PayCreateRequest reqDTO) {
        String channel = reqDTO.getChannel() != null ? reqDTO.getChannel() : "MOCK";
        PaymentStrategy strategy = getStrategy(channel);
        return strategy.createPayment(reqDTO);
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void handleCallback(PayCallbackReqDTO reqDTO) {
        payNotifyChainContext.handler(ChainMarkEnum.PAY_NOTIFY.name(), reqDTO);

        log.info("收到支付回调, orderSn={}, status={}, tradeNo={}",
                reqDTO.getOrderSn(), reqDTO.getStatus(), reqDTO.getTradeNo());

        String orderSn = reqDTO.getOrderSn();
        boolean success = "SUCCESS".equalsIgnoreCase(reqDTO.getStatus());

        if (!success) {
            saveOrUpdatePay(reqDTO, PayStatusEnum.FAIL.getCode());
            log.warn("支付失败, orderSn={}", orderSn);
            return;
        }

        // CAS: UNPAID → PAID
        int updated = orderMapper.update(null,
                Wrappers.lambdaUpdate(Order.class)
                        .eq(Order::getOrderSn, orderSn)
                        .eq(Order::getStatus, OrderStatusEnum.UNPAID.getCode())
                        .set(Order::getStatus, OrderStatusEnum.PAID.getCode())
                        .set(Order::getPayTime, LocalDateTime.now())
        );

        if (updated > 0) {
            orderItemMapper.update(null,
                    Wrappers.lambdaUpdate(com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem.class)
                            .eq(com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem::getOrderSn, orderSn)
                            .set(com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem::getStatus, TicketStatusEnum.PAID.getCode())
            );
            ticketMapper.update(null,
                    Wrappers.lambdaUpdate(com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket.class)
                            .eq(com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket::getOrderSn, orderSn)
                            .set(com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket::getTicketStatus, TicketStatusEnum.PAID.getCode())
            );
            saveOrUpdatePay(reqDTO, PayStatusEnum.SUCCESS.getCode());
            log.info("支付成功, orderSn={}", orderSn);
            return;
        }

        // CAS 失败 → 重新读取当前状态
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            log.warn("支付回调-订单不存在, orderSn={}", orderSn);
            throw new ClientException("订单不存在");
        }

        Integer currentStatus = order.getStatus();
        if (OrderStatusEnum.PAID.getCode().equals(currentStatus)) {
            log.info("支付回调-订单已支付（幂等）, orderSn={}", orderSn);
            saveOrUpdatePay(reqDTO, PayStatusEnum.SUCCESS.getCode());
            return;
        }

        if (OrderStatusEnum.CANCELED.getCode().equals(currentStatus)) {
            log.warn("支付回调-订单已取消，记录待退款, orderSn={}", orderSn);
            saveOrUpdatePay(reqDTO, PayStatusEnum.PENDING_REFUND.getCode());
            return;
        }

        log.warn("支付回调-订单状态异常, orderSn={}, status={}", orderSn, currentStatus);
        throw new ClientException("订单状态异常");
    }

    @Override
    public PayInfoDTO queryPayment(String orderSn) {
        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
        );
        if (pay == null) {
            return null;
        }
        return PayInfoDTO.builder()
                .paySn(pay.getPaySn())
                .channel(pay.getChannel())
                .tradeNo(pay.getTradeNo())
                .totalAmount(pay.getTotalAmount())
                .status(pay.getStatus())
                .gmtPayment(pay.getGmtPayment())
                .build();
    }

    @Override
    public void refund(String orderSn, Integer amount) {
        PaymentStrategy strategy = getStrategy("MOCK");
        strategy.refund(orderSn, amount);
    }

    public Pay getPayByOrderSn(String orderSn) {
        return payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
        );
    }

    public Pay getPayByPaySn(String paySn) {
        return payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getPaySn, paySn)
        );
    }

    private void saveOrUpdatePay(PayCallbackReqDTO reqDTO, String status) {
        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, reqDTO.getOrderSn())
        );
        if (pay != null) {
            pay.setTradeNo(reqDTO.getTradeNo());
            pay.setChannel(reqDTO.getChannel());
            pay.setStatus(status);
            pay.setGmtPayment(PayStatusEnum.SUCCESS.getCode().equals(status) ? LocalDateTime.now() : pay.getGmtPayment());
            if (reqDTO.getTotalAmount() != null) {
                pay.setTotalAmount(reqDTO.getTotalAmount());
            }
            payMapper.updateById(pay);
            return;
        }
        try {
            Pay newPay = Pay.builder()
                    .paySn(String.valueOf(snowflakeUtil.generateId()))
                    .orderSn(reqDTO.getOrderSn())
                    .tradeNo(reqDTO.getTradeNo())
                    .channel(reqDTO.getChannel())
                    .totalAmount(reqDTO.getTotalAmount())
                    .status(status)
                    .gmtPayment(PayStatusEnum.SUCCESS.getCode().equals(status) ? LocalDateTime.now() : null)
                    .build();
            payMapper.insert(newPay);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("支付记录已存在（并发插入），改为更新, orderSn={}", reqDTO.getOrderSn());
            payMapper.update(null,
                    Wrappers.lambdaUpdate(Pay.class)
                            .eq(Pay::getOrderSn, reqDTO.getOrderSn())
                            .set(Pay::getTradeNo, reqDTO.getTradeNo())
                            .set(Pay::getChannel, reqDTO.getChannel())
                            .set(Pay::getStatus, status)
                            .set(Pay::getGmtPayment, PayStatusEnum.SUCCESS.getCode().equals(status) ? LocalDateTime.now() : null)
            );
            if (reqDTO.getTotalAmount() != null) {
                payMapper.update(null,
                        Wrappers.lambdaUpdate(Pay.class)
                                .eq(Pay::getOrderSn, reqDTO.getOrderSn())
                                .set(Pay::getTotalAmount, reqDTO.getTotalAmount())
                );
            }
        }
    }
}
