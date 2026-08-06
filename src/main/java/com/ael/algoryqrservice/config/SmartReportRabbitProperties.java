package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "smart-report.rabbitmq")
public class SmartReportRabbitProperties {

    private String queue = "smart_report.generate";
    private String eventsExchange = "smart_report.events";
    private String eventsQueue = "qr-service.smart_report.events";
    private String eventsRoutingKey = "smart_report.status";
}
