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
    public Queue paymentEventsQueue(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getEventsQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getEventsQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue paymentEventsDlq(PaymentRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getEventsQueue() + ".dlq").build();
    }

    @Bean
    public Binding paymentEventsBinding(
            Queue paymentEventsQueue,
            TopicExchange paymentEventsExchange,
            PaymentRabbitMqProperties properties
    ) {
        return BindingBuilder.bind(paymentEventsQueue)
                .to(paymentEventsExchange)
                .with(properties.getEventsRoutingKey());
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange pushNotificationExchange(PushNotificationProperties properties) {
        return new TopicExchange(properties.getMessaging().getExchange(), true, false);
    }
}
