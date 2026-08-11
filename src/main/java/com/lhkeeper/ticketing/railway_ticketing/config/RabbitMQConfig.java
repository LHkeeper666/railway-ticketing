package com.lhkeeper.ticketing.railway_ticketing.config;

import com.alibaba.fastjson2.JSON;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 配置，声明抢票队列（含 DLX）和订单超时取消延迟队列（TTL + DLX）
 */
@Configuration
public class RabbitMQConfig {

    public static final String FLASH_ORDER_QUEUE = "flash.order.queue";
    public static final String FLASH_ORDER_EXCHANGE = "flash.order.exchange";
    public static final String FLASH_ORDER_ROUTING_KEY = "flash.order.create";
    public static final String FLASH_ORDER_DLQ = "flash.order.dlq";
    public static final String FLASH_ORDER_DLX = "flash.order.dlx";

    // ==================== Order Timeout Cancel (TTL + DLX) ====================

    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_DELAY_EXCHANGE = "order.timeout.delay.exchange";
    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";

    public static final String ORDER_TIMEOUT_DLX = "order.timeout.dlx";
    public static final String ORDER_TIMEOUT_CANCEL_QUEUE = "order.timeout.cancel.queue";
    public static final String ORDER_TIMEOUT_CANCEL_ROUTING_KEY = "order.timeout.cancel";

    /** 订单支付超时（毫秒），默认 15 分钟 */
    public static final String ORDER_TIMEOUT_MS = String.valueOf(15 * 60 * 1000);

    // ==================== Waitlist Match ====================

    public static final String WAITLIST_MATCH_QUEUE = "waitlist.match.queue";
    public static final String WAITLIST_MATCH_EXCHANGE = "waitlist.match.exchange";
    public static final String WAITLIST_MATCH_ROUTING_KEY = "waitlist.match.trigger";
    public static final String WAITLIST_MATCH_DLQ = "waitlist.match.dlq";
    public static final String WAITLIST_MATCH_DLX = "waitlist.match.dlx";

    // ==================== Order Status Event ====================

    public static final String ORDER_STATUS_QUEUE = "order.status.queue";
    public static final String ORDER_STATUS_EXCHANGE = "order.status.exchange";
    public static final String ORDER_STATUS_ROUTING_KEY = "order.status.changed";

    @Bean
    public Queue flashOrderQueue() {
        return QueueBuilder.durable(FLASH_ORDER_QUEUE)
                .deadLetterExchange(FLASH_ORDER_DLX)
                .deadLetterRoutingKey(FLASH_ORDER_DLQ)
                .build();
    }

    @Bean
    public DirectExchange flashOrderExchange() {
        return new DirectExchange(FLASH_ORDER_EXCHANGE);
    }

    @Bean
    public Binding flashOrderBinding() {
        return BindingBuilder.bind(flashOrderQueue())
                .to(flashOrderExchange())
                .with(FLASH_ORDER_ROUTING_KEY);
    }

    @Bean
    public Queue flashOrderDlq() {
        return new Queue(FLASH_ORDER_DLQ, true);
    }

    @Bean
    public DirectExchange flashOrderDlx() {
        return new DirectExchange(FLASH_ORDER_DLX);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(flashOrderDlq())
                .to(flashOrderDlx())
                .with(FLASH_ORDER_DLQ);
    }

    // ==================== Order Timeout Cancel Beans ====================

    @Bean
    public Queue orderTimeoutDelayQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_DELAY_QUEUE)
                .deadLetterExchange(ORDER_TIMEOUT_DLX)
                .deadLetterRoutingKey(ORDER_TIMEOUT_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderTimeoutDelayExchange() {
        return new DirectExchange(ORDER_TIMEOUT_DELAY_EXCHANGE);
    }

    @Bean
    public Binding orderTimeoutDelayBinding() {
        return BindingBuilder.bind(orderTimeoutDelayQueue())
                .to(orderTimeoutDelayExchange())
                .with(ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Queue orderTimeoutCancelQueue() {
        return new Queue(ORDER_TIMEOUT_CANCEL_QUEUE, true);
    }

    @Bean
    public DirectExchange orderTimeoutDlx() {
        return new DirectExchange(ORDER_TIMEOUT_DLX);
    }

    @Bean
    public Binding orderTimeoutCancelBinding() {
        return BindingBuilder.bind(orderTimeoutCancelQueue())
                .to(orderTimeoutDlx())
                .with(ORDER_TIMEOUT_CANCEL_ROUTING_KEY);
    }

    // ==================== Waitlist Match Beans ====================

    @Bean
    public Queue waitlistMatchQueue() {
        return QueueBuilder.durable(WAITLIST_MATCH_QUEUE)
                .deadLetterExchange(WAITLIST_MATCH_DLX)
                .deadLetterRoutingKey(WAITLIST_MATCH_DLQ)
                .build();
    }

    @Bean
    public DirectExchange waitlistMatchExchange() {
        return new DirectExchange(WAITLIST_MATCH_EXCHANGE);
    }

    @Bean
    public Binding waitlistMatchBinding() {
        return BindingBuilder.bind(waitlistMatchQueue())
                .to(waitlistMatchExchange())
                .with(WAITLIST_MATCH_ROUTING_KEY);
    }

    @Bean
    public Queue waitlistMatchDlq() {
        return new Queue(WAITLIST_MATCH_DLQ, true);
    }

    @Bean
    public DirectExchange waitlistMatchDlx() {
        return new DirectExchange(WAITLIST_MATCH_DLX);
    }

    @Bean
    public Binding waitlistMatchDlqBinding() {
        return BindingBuilder.bind(waitlistMatchDlq())
                .to(waitlistMatchDlx())
                .with(WAITLIST_MATCH_DLQ);
    }

    @Bean
    public MessageConverter fastjsonMessageConverter() {
        return new MessageConverter() {
            @Override
            public org.springframework.amqp.core.Message toMessage(Object object, MessageProperties messageProperties) {
                byte[] bytes = JSON.toJSONBytes(object);
                return new org.springframework.amqp.core.Message(bytes, messageProperties);
            }

            @Override
            public Object fromMessage(org.springframework.amqp.core.Message message) {
                return new String(message.getBody(), StandardCharsets.UTF_8);
            }
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(fastjsonMessageConverter());
        return template;
    }

    // ==================== Order Status Event Beans ====================

    @Bean
    public Queue orderStatusQueue() {
        return new Queue(ORDER_STATUS_QUEUE, true);
    }

    @Bean
    public DirectExchange orderStatusExchange() {
        return new DirectExchange(ORDER_STATUS_EXCHANGE);
    }

    @Bean
    public Binding orderStatusBinding() {
        return BindingBuilder.bind(orderStatusQueue())
                .to(orderStatusExchange())
                .with(ORDER_STATUS_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(fastjsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
