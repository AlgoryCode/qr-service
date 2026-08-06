package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    private static final String PAYMENT_COMPLETED_TYPE_ID = "payment.completed";
    private static final String PAYMENT_COMPLETED_LEGACY_TYPE_ID =
            "com.paymentservice.domain.model.PaymentCompletedEvent";

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
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(Map.of(
                PAYMENT_COMPLETED_TYPE_ID, PaymentCompletedEventDto.class,
                PAYMENT_COMPLETED_LEGACY_TYPE_ID, PaymentCompletedEventDto.class
        ));
        converter.setClassMapper(classMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public TopicExchange pushNotificationExchange(PushNotificationProperties properties) {
        return new TopicExchange(properties.getMessaging().getExchange(), true, false);
    }
}
