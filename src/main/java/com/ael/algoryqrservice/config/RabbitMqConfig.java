package com.ael.algoryqrservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange paymentEventsExchange(PaymentRabbitMqProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue paymentSuccessQueue(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getSuccessQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getSuccessQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue paymentSuccessDlq(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getSuccessQueue() + ".dlq").build();
    }

    @Bean
    public Queue paymentFailedQueue(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getFailedQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getFailedQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue paymentFailedDlq(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getFailedQueue() + ".dlq").build();
    }

    @Bean
    public Binding paymentSuccessBinding(
            Queue paymentSuccessQueue,
            TopicExchange paymentEventsExchange,
            PaymentRabbitMqProperties properties
    ) {
        return BindingBuilder.bind(paymentSuccessQueue)
                .to(paymentEventsExchange)
                .with(properties.getSuccessRoutingKey());
    }

    @Bean
    public Binding paymentFailedBinding(
            Queue paymentFailedQueue,
            TopicExchange paymentEventsExchange,
            PaymentRabbitMqProperties properties
    ) {
        return BindingBuilder.bind(paymentFailedQueue)
                .to(paymentEventsExchange)
                .with(properties.getFailedRoutingKey());
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
