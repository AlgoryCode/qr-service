package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.config.MenuImportRabbitProperties;
import com.ael.algoryqrservice.model.AiMenuImportJob;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiMenuImportMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MenuImportRabbitProperties properties;

    public void publishAiRequested(AiMenuImportJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId());
        body.put("tenantId", job.getTenantId());
        body.put("menuId", job.getMenuId());
        body.put("attempt", 1);
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getAiRequestedRoutingKey(),
                body
        );
    }
}
