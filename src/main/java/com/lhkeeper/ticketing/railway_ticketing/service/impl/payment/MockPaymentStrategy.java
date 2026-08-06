package com.lhkeeper.ticketing.railway_ticketing.service.impl.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.PayCreateRequest;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PayCreateResult;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Pay;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderItemMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PayMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.PaymentStrategy;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Mock 支付策略，使用 HMAC-SHA256 签名模拟完整支付链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentStrategy implements PaymentStrategy {

    public static final String CHANNEL = "MOCK";

    private final PayMapper payMapper;
    private final OrderItemMapper orderItemMapper;
    private final SnowflakeUtil snowflakeUtil;

    @Value("${payment.mock.secret:mock-secret-default}")
    private String mockSecret;

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public PayCreateResult createPayment(PayCreateRequest request) {
        // 防重复支付
        Pay existing = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, request.getOrderSn())
        );
        if (existing != null) {
            throw new ClientException("订单已有支付记录，请勿重复支付");
        }

        // 计算订单金额
        BigDecimal totalAmount = request.getTotalAmount();
        if (totalAmount == null) {
            List<OrderItem> orderItems = orderItemMapper.selectList(
                    Wrappers.lambdaQuery(OrderItem.class)
                            .eq(OrderItem::getOrderSn, request.getOrderSn())
            );
            totalAmount = BigDecimal.ZERO;
            for (OrderItem orderItem : orderItems) {
                totalAmount = totalAmount.add(orderItem.getAmount());
            }
        }

        String paySn = String.valueOf(snowflakeUtil.generateId());
        String sign = generateSign(paySn, request.getOrderSn(), totalAmount, PayStatusEnum.PENDING.getCode());

        Pay pay = Pay.builder()
                .paySn(paySn)
                .orderSn(request.getOrderSn())
                .channel(CHANNEL)
                .subject(request.getSubject())
                .totalAmount(totalAmount)
                .tradeNo(sign) // 复用 trade_no 存储签名
                .status(PayStatusEnum.PENDING.getCode())
                .build();
        try {
            payMapper.insert(pay);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ClientException("订单已有支付记录，请勿重复支付");
        }

        log.info("Mock 支付记录已创建, orderSn={}, paySn={}, amount={}", request.getOrderSn(), paySn, totalAmount);

        return PayCreateResult.builder()
                .paySn(paySn)
                .orderSn(request.getOrderSn())
                .totalAmount(totalAmount)
                .payUrl("/mock-pay/" + paySn)
                .sign(sign)
                .build();
    }

    @Override
    public boolean verifySignature(PayCallbackReqDTO callback) {
        String paySn = findPaySnByOrderSn(callback.getOrderSn());
        if (paySn == null) {
            return false;
        }
        String expectedSign = generateSign(paySn, callback.getOrderSn(),
                callback.getTotalAmount(), callback.getStatus().toUpperCase());
        return expectedSign.equals(callback.getSign());
    }

    @Override
    public String queryStatus(String orderSn) {
        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
        );
        return pay != null ? pay.getStatus() : null;
    }

    @Override
    public boolean refund(String orderSn, Integer amount) {
        log.info("Mock 退款, orderSn={}, amount={}", orderSn, amount);
        // Mock 退款直接返回成功
        return true;
    }

    public String generateSign(String paySn, String orderSn, BigDecimal totalAmount, String status) {
        String payload = paySn + "|" + orderSn + "|" + totalAmount + "|" + status;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    mockSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("签名生成失败", e);
        }
    }

    private String findPaySnByOrderSn(String orderSn) {
        Pay pay = payMapper.selectOne(
                Wrappers.lambdaQuery(Pay.class)
                        .eq(Pay::getOrderSn, orderSn)
        );
        return pay != null ? pay.getPaySn() : null;
    }
}
