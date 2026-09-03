package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.service.IntegrationPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationPublishConsumer {

    private final IntegrationPublishService publishService;

    @RabbitListener(queues = "#{integrationRabbitProperties.publishQueue}")
    public void consume(PublishRequestedMessage message) {
        if (message == null || message.pendingProductId() == null) {
            throw new AmqpRejectAndDontRequeueException("pendingProductId zorunludur");
        }
        try {
            publishService.publish(message.pendingProductId());
        } catch (IllegalStateException retryable) {
            log.warn("Publish retry pendingProductId={}", message.pendingProductId(), retryable);
            throw retryable;
        } catch (RuntimeException exception) {
            log.error("Publish failed permanently pendingProductId={}", message.pendingProductId(), exception);
            throw new AmqpRejectAndDontRequeueException(exception.getMessage(), exception);
        }
    }

    public record PublishRequestedMessage(
            UUID pendingProductId,
            Long menuId,
            Object publishTargets,
            Integer attempt
    ) {
    }
}
