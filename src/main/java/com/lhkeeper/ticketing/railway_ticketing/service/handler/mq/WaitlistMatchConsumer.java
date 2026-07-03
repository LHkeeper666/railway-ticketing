package com.lhkeeper.ticketing.railway_ticketing.service.handler.mq;

import com.alibaba.fastjson2.JSON;
import com.lhkeeper.ticketing.railway_ticketing.config.RabbitMQConfig;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.WaitlistMatchMessageDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
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
public class WaitlistMatchConsumer {

    private final WaitlistService waitlistService;

    @RabbitListener(queues = RabbitMQConfig.WAITLIST_MATCH_QUEUE, concurrency = "3")
    public void onMessage(String messageBody, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到候补匹配消息, body={}", messageBody);
        try {
            WaitlistMatchMessageDTO msg = JSON.parseObject(messageBody, WaitlistMatchMessageDTO.class);
            waitlistService.processMatch(msg.getTrainId(), msg.getStartStation(), msg.getEndStation());
            channel.basicAck(deliveryTag, false);
            log.info("候补匹配处理完成, trainId={}", msg.getTrainId());
        } catch (Exception e) {
            log.error("候补匹配处理失败，重新入队, body={}", messageBody, e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("nack 失败", ex);
            }
        }
    }
}
