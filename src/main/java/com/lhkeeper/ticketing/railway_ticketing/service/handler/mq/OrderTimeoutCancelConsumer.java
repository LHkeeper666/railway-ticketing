package com.lhkeeper.ticketing.railway_ticketing.service.handler.mq;

import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
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
 * 订单超时取消消费者，直接委托 cancelOrder（内部 CAS 保证原子性）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_CANCEL_QUEUE)
    public void onMessage(String orderSn, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到订单超时取消消息, orderSn={}", orderSn);
        try {
            orderService.cancelOrder(orderSn, true);
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
