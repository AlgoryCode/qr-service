package com.ael.algoryqrservice.fulfillmentservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingLifecycleProperties.class)
public class FulfillmentModuleConfiguration {
}
