package com.ael.algoryqrservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
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
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory paymentListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }

    @Bean
    public TopicExchange pushNotificationExchange(PushNotificationProperties properties) {
        return new TopicExchange(properties.getMessaging().getExchange(), true, false);
    }

    @Bean
    public TopicExchange menuEventsExchange(MenuEventsRabbitProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public TopicExchange integrationEventsExchange(IntegrationRabbitProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue integrationAiRequestedQueue(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getAiRequestedQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getAiRequestedQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue integrationAiRequestedDlq(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getAiRequestedQueue() + ".dlq").build();
    }

    @Bean
    public Queue integrationAiCompletedQueue(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getAiCompletedQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getAiCompletedQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue integrationAiCompletedDlq(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getAiCompletedQueue() + ".dlq").build();
    }

    @Bean
    public Queue integrationPublishQueue(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getPublishQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.getPublishQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue integrationPublishDlq(IntegrationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getPublishQueue() + ".dlq").build();
    }

    @Bean
    public Binding integrationAiRequestedBinding(
            Queue integrationAiRequestedQueue,
            TopicExchange integrationEventsExchange,
            IntegrationRabbitProperties properties
    ) {
        return BindingBuilder.bind(integrationAiRequestedQueue)
                .to(integrationEventsExchange)
                .with(properties.getAiRequestedRoutingKey());
    }

    @Bean
    public Binding integrationAiCompletedBinding(
            Queue integrationAiCompletedQueue,
            TopicExchange integrationEventsExchange,
            IntegrationRabbitProperties properties
    ) {
        return BindingBuilder.bind(integrationAiCompletedQueue)
                .to(integrationEventsExchange)
                .with(properties.getAiCompletedRoutingKey());
    }

    @Bean
    public Binding integrationPublishBinding(
            Queue integrationPublishQueue,
            TopicExchange integrationEventsExchange,
            IntegrationRabbitProperties properties
    ) {
        return BindingBuilder.bind(integrationPublishQueue)
                .to(integrationEventsExchange)
                .with(properties.getPublishRoutingKey());
    }
}
