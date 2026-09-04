package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.config.IntegrationRabbitProperties;
import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IntegrationRabbitProperties properties;

    public void publishAiRequested(IntegrationJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId());
        body.put("tenantId", job.getTenantId());
        body.put("menuId", job.getMenuId());
        body.put("direction", job.getDirection());
        body.put("snapshotVersion", job.getSnapshotVersion());
        body.put("attempt", 1);
        String exchange = properties.getExchange();
        String routingKey = properties.getAiRequestedRoutingKey();
        log.info(
                "integration_ai_requested_publish_start jobId={} exchange={} routingKey={} direction={} menuId={}",
                job.getId(), exchange, routingKey, job.getDirection(), job.getMenuId()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, body);
            log.info(
                    "integration_ai_requested_published jobId={} exchange={} routingKey={}",
                    job.getId(), exchange, routingKey
            );
        } catch (RuntimeException ex) {
            log.error(
                    "integration_ai_requested_publish_failed jobId={} exchange={} routingKey={}",
                    job.getId(), exchange, routingKey, ex
            );
            throw ex;
        }
    }

    public void publishApprovedProduct(IntegrationPendingProduct product) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pendingProductId", product.getId());
        body.put("menuId", product.getMenuId());
        body.put("publishTargets", product.getPublishTargets());
        body.put("attempt", 1);
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getPublishRoutingKey(),
                body
        );
    }
}
