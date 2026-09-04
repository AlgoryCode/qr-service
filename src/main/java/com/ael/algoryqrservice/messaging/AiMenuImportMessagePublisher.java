package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.config.MenuImportRabbitProperties;
import com.ael.algoryqrservice.model.AiMenuImportJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
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
        String exchange = properties.getExchange();
        String routingKey = properties.getAiRequestedRoutingKey();
        log.info(
                "menu_import_ai_requested_publish_start jobId={} exchange={} routingKey={} menuId={}",
                job.getId(), exchange, routingKey, job.getMenuId()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, body);
            log.info(
                    "menu_import_ai_requested_published jobId={} exchange={} routingKey={}",
                    job.getId(), exchange, routingKey
            );
        } catch (RuntimeException ex) {
            log.error(
                    "menu_import_ai_requested_publish_failed jobId={} exchange={} routingKey={}",
                    job.getId(), exchange, routingKey, ex
            );
            throw ex;
        }
    }
}
