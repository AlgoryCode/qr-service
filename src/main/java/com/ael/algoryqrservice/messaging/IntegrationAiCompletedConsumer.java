package com.ael.algoryqrservice.messaging;

import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.ael.algoryqrservice.model.enums.IntegrationDirection;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.repository.IntegrationJobRepository;
import com.ael.algoryqrservice.repository.IntegrationPendingProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationAiCompletedConsumer {

    private final IntegrationJobRepository jobRepository;
    private final IntegrationPendingProductRepository pendingProductRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{integrationRabbitProperties.aiCompletedQueue}")
    @Transactional
    public void consume(AiCompletedMessage message) {
        if (message == null || message.jobId() == null) {
            throw new AmqpRejectAndDontRequeueException("jobId zorunludur");
        }
        IntegrationJob job = jobRepository.findById(message.jobId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Integration job bulunamadı"));

        LocalDateTime now = LocalDateTime.now();
        List<AiProduct> products = message.products() == null ? List.of() : message.products();
        for (AiProduct product : products) {
            persistPending(job, message, product, now);
        }
        if (message.errors() != null) {
            for (AiError error : message.errors()) {
                persistFailed(job, message, error, now);
            }
        }

        job.setStatus(IntegrationJobStatus.WAITING_APPROVAL);
        job.setFinishedAt(now);
        jobRepository.save(job);
    }

    private void persistPending(IntegrationJob job, AiCompletedMessage message, AiProduct product, LocalDateTime now) {
        if (product == null || product.sourceProductId() == null || product.sourceProductId().isBlank()) {
            return;
        }
        if (pendingProductRepository.existsByJobIdAndSourceProductId(job.getId(), product.sourceProductId())) {
            return;
        }
        ObjectNode productData = mergeProductData(job, product);
        pendingProductRepository.save(IntegrationPendingProduct.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .tenantId(job.getTenantId())
                .menuId(job.getMenuId())
                .source(resolveSource(job, message))
                .sourceProductId(product.sourceProductId())
                .productData(productData)
                .confidence(product.confidence())
                .approvalStatus(IntegrationJobStatus.WAITING_APPROVAL)
                .publishTargets(new LinkedHashSet<>())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void persistFailed(IntegrationJob job, AiCompletedMessage message, AiError error, LocalDateTime now) {
        if (error == null || error.sourceProductId() == null || error.sourceProductId().isBlank()) {
            return;
        }
        if (pendingProductRepository.existsByJobIdAndSourceProductId(job.getId(), error.sourceProductId())) {
            return;
        }
        ObjectNode productData = objectMapper.createObjectNode();
        productData.put("sourceProductId", error.sourceProductId());
        productData.putArray("warnings");
        pendingProductRepository.save(IntegrationPendingProduct.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .tenantId(job.getTenantId())
                .menuId(job.getMenuId())
                .source(resolveSource(job, message))
                .sourceProductId(error.sourceProductId())
                .productData(productData)
                .approvalStatus(IntegrationJobStatus.FAILED)
                .publishTargets(new LinkedHashSet<>())
                .errorMessage(error.message())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private ObjectNode mergeProductData(IntegrationJob job, AiProduct product) {
        ObjectNode data = objectMapper.createObjectNode();
        JsonNode snapshotProduct = findSnapshotProduct(job.getSnapshot(), product.sourceProductId());
        if (snapshotProduct != null && snapshotProduct.isObject()) {
            data.setAll((ObjectNode) snapshotProduct);
        }
        JsonNode mapping = product.mapping();
        if (mapping != null && mapping.isObject()) {
            copyText(mapping, data, "category");
            copyText(mapping, data, "subcategory");
            copyNonBlankAs(mapping, data, "translatedName", "name");
            copyNonBlankAs(mapping, data, "translatedDescription", "description");
            if (mapping.has("modifierGroupIds")) {
                data.set("modifierGroupIds", mapping.get("modifierGroupIds"));
            }
        }
        data.put("sourceProductId", product.sourceProductId());
        if (product.customId() != null) {
            data.put("customId", product.customId());
        }
        ArrayNode warnings = data.putArray("warnings");
        if (product.warnings() != null) {
            for (String warning : product.warnings()) {
                if (warning != null && !warning.isBlank()) {
                    warnings.add(warning);
                }
            }
        }
        return data;
    }

    private JsonNode findSnapshotProduct(JsonNode snapshot, String sourceProductId) {
        if (snapshot == null || !snapshot.has("products") || !snapshot.get("products").isArray()) {
            return null;
        }
        for (JsonNode node : snapshot.get("products")) {
            if (node == null) {
                continue;
            }
            if (sourceProductId.equals(text(node, "sourceProductId"))
                    || sourceProductId.equals(text(node, "internalProductId"))) {
                return node;
            }
        }
        return null;
    }

    private String resolveSource(IntegrationJob job, AiCompletedMessage message) {
        if (message.direction() != null && message.direction().contains("IMPORT")) {
            return "UBEREATS";
        }
        if (IntegrationDirection.IMPORT_FROM_UBEREATS.equals(job.getDirection())) {
            return "UBEREATS";
        }
        return "INTERNAL";
    }

    private void copyText(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            String value = source.get(field).asText();
            if (value != null && !value.isBlank()) {
                target.put(field, value);
            }
        }
    }

    private void copyNonBlankAs(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        if (!source.hasNonNull(sourceField)) {
            return;
        }
        String value = source.get(sourceField).asText();
        if (value == null || value.isBlank()) {
            return;
        }
        target.put(targetField, value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    public record AiCompletedMessage(
            UUID jobId,
            Long menuId,
            String direction,
            String status,
            List<AiProduct> products,
            List<AiError> errors
    ) {
    }

    public record AiProduct(
            String customId,
            String sourceProductId,
            BigDecimal confidence,
            JsonNode mapping,
            List<String> warnings
    ) {
    }

    public record AiError(
            String sourceProductId,
            String message
    ) {
    }
}
