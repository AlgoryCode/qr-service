package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.config.IntegrationRabbitProperties;
import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getAiRequestedRoutingKey(),
                body
        );
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
