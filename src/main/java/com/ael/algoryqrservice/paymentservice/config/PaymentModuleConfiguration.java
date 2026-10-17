package com.ael.algoryqrservice.paymentservice.config;

import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentCallbackProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentEventsRabbitMqProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentGatewayProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentReconciliationProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentTokenProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PushNotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        PaytrProperties.class,
        PushNotificationProperties.class,
        PaymentCallbackProperties.class,
        PaymentEventsRabbitMqProperties.class,
        PaymentGatewayProperties.class,
        PaymentReconciliationProperties.class,
        PaymentTokenProperties.class
})
public class PaymentModuleConfiguration {
}
