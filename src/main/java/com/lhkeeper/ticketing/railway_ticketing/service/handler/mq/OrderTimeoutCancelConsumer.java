package com.lhkeeper.ticketing.railway_ticketing.service.handler.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消消费者，消费延迟队列中的超时订单并执行取消
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelConsumer {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_CANCEL_QUEUE)
    public void onMessage(String orderSn, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到订单超时取消消息, orderSn={}", orderSn);
        try {
            Order order = orderMapper.selectOne(
                    Wrappers.lambdaQuery(Order.class)
                            .eq(Order::getOrderSn, orderSn)
            );
            if (order == null) {
                log.warn("超时取消-订单不存在, orderSn={}", orderSn);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
                log.info("订单已处理，跳过超时取消, orderSn={}, status={}",
                        orderSn, order.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }
            orderService.cancelOrder(orderSn);
            channel.basicAck(deliveryTag, false);
            log.info("超时取消订单成功, orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("超时取消订单失败, orderSn={}", orderSn, e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("nack 失败", ex);
            }
        }
    }
}
