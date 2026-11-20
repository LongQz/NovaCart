package com.novacart.mall.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ order event topology. */
@Configuration
public class RabbitConfig {
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("novacart.order.events");
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue("novacart.order.paid", true);
    }

    @Bean
    public DirectExchange orderTimeoutDelayExchange() {
        return new DirectExchange("novacart.order.timeout.delay", true, false);
    }

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange("novacart.order.timeout.exchange", true, false);
    }

    @Bean
    public Queue orderTimeoutWaitQueue(
            @Value("${novacart.order.timeout-ms:1800000}") long timeoutMs
    ) {
        return QueueBuilder.durable("novacart.order.timeout.wait")
                .ttl((int) Math.min(Integer.MAX_VALUE, timeoutMs))
                .deadLetterExchange("novacart.order.timeout.exchange")
                .deadLetterRoutingKey("order.timeout")
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue("novacart.order.timeout", true);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(orderExchange).with("order.paid");
    }

    @Bean
    public Binding orderTimeoutWaitBinding(
            Queue orderTimeoutWaitQueue,
            DirectExchange orderTimeoutDelayExchange
    ) {
        return BindingBuilder.bind(orderTimeoutWaitQueue)
                .to(orderTimeoutDelayExchange)
                .with("order.timeout.wait");
    }

    @Bean
    public Binding orderTimeoutBinding(
            Queue orderTimeoutQueue,
            DirectExchange orderTimeoutExchange
    ) {
        return BindingBuilder.bind(orderTimeoutQueue)
                .to(orderTimeoutExchange)
                .with("order.timeout");
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
