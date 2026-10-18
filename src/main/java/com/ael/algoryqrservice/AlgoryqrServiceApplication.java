package com.ael.algoryqrservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRabbit
@EntityScan(
        basePackages = {
                "com.ael.algoryqrservice.qrservice.model",
                "com.ael.algoryqrservice.qrservice.repository",
                "com.ael.algoryqrservice.qrservice.product.model",
                "com.ael.algoryqrservice.qrservice.qr.model",
                "com.ael.algoryqrservice.qrservice.store.model",
                "com.ael.algoryqrservice.qrservice.store.repository",
                "com.ael.algoryqrservice.qrservice.print.model",
                "com.ael.algoryqrservice.qrservice.integration.ubereats.model",
                "com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model",
                "com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model",
                "com.ael.algoryqrservice.merchantservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.billservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.customerservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity",
        }
)
public class AlgoryqrServiceApplication {

    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(AlgoryqrServiceApplication.class, args);
    }
}
