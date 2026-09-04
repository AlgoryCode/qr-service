package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.model.AiMenuImportDraft;
import com.ael.algoryqrservice.model.AiMenuImportJob;
import com.ael.algoryqrservice.model.enums.AiMenuImportJobStatus;
import com.ael.algoryqrservice.repository.AiMenuImportDraftRepository;
import com.ael.algoryqrservice.repository.AiMenuImportJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMenuImportCompletedConsumer {

    private final AiMenuImportJobRepository jobRepository;
    private final AiMenuImportDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{menuImportRabbitProperties.aiCompletedQueue}")
    @Transactional
    public void consume(AiCompletedMessage message) {
        if (message == null || message.jobId() == null) {
            throw new AmqpRejectAndDontRequeueException("jobId zorunludur");
        }
        AiMenuImportJob job = jobRepository.findById(message.jobId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("AI menu import job bulunamadı"));

        LocalDateTime now = LocalDateTime.now();
        List<AiProduct> products = message.products() == null ? List.of() : message.products();
        for (AiProduct product : products) {
            persistDraft(job, product, now);
        }
        if (message.errors() != null) {
            for (AiError error : message.errors()) {
                persistFailed(job, error, now);
            }
        }

        if ("FAILED".equalsIgnoreCase(message.status()) && products.isEmpty()) {
            job.setStatus(AiMenuImportJobStatus.FAILED);
            job.setErrorMessage(message.errorMessage());
        } else {
            job.setStatus(AiMenuImportJobStatus.WAITING_APPROVAL);
        }
        job.setFinishedAt(now);
        jobRepository.save(job);
    }

    private void persistDraft(AiMenuImportJob job, AiProduct product, LocalDateTime now) {
        if (product == null || product.sourceProductId() == null || product.sourceProductId().isBlank()) {
            return;
        }
        if (draftRepository.existsByJobIdAndSourceProductId(job.getId(), product.sourceProductId())) {
            return;
        }
        ObjectNode data = objectMapper.createObjectNode();
        JsonNode productData = product.productData();
        if (productData != null && productData.isObject()) {
            data.setAll((ObjectNode) productData);
        }
        data.put("sourceProductId", product.sourceProductId());
        draftRepository.save(AiMenuImportDraft.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .tenantId(job.getTenantId())
                .menuId(job.getMenuId())
                .sourceProductId(product.sourceProductId())
                .productData(data)
                .confidence(product.confidence())
                .approvalStatus(AiMenuImportJobStatus.WAITING_APPROVAL)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void persistFailed(AiMenuImportJob job, AiError error, LocalDateTime now) {
        if (error == null || error.sourceProductId() == null || error.sourceProductId().isBlank()) {
            return;
        }
        if (draftRepository.existsByJobIdAndSourceProductId(job.getId(), error.sourceProductId())) {
            return;
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("sourceProductId", error.sourceProductId());
        draftRepository.save(AiMenuImportDraft.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .tenantId(job.getTenantId())
                .menuId(job.getMenuId())
                .sourceProductId(error.sourceProductId())
                .productData(data)
                .approvalStatus(AiMenuImportJobStatus.FAILED)
                .errorMessage(error.message())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public record AiCompletedMessage(
            UUID jobId,
            Long menuId,
            String status,
            String errorMessage,
            List<AiProduct> products,
            List<AiError> errors
    ) {
    }

    public record AiProduct(
            String sourceProductId,
            BigDecimal confidence,
            JsonNode productData
    ) {
    }

    public record AiError(
            String sourceProductId,
            String message
    ) {
    }
}
