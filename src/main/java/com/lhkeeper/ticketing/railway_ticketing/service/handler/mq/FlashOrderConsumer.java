package com.lhkeeper.ticketing.railway_ticketing.service.handler.mq;

import com.alibaba.fastjson2.JSON;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.FlashOrderMessageDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashOrderConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.FLASH_ORDER_QUEUE)
    public void onMessage(String messageBody, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到抢票消息, body={}", messageBody);
        try {
            FlashOrderMessageDTO msg = JSON.parseObject(messageBody, FlashOrderMessageDTO.class);
            orderService.processFlashOrder(msg);
            channel.basicAck(deliveryTag, false);
            log.info("抢票处理成功, orderSn={}", msg.getOrderSn());
        } catch (Exception e) {
            log.error("抢票处理失败, body={}", messageBody, e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("nack 失败", ex);
            }
        }
    }
}
