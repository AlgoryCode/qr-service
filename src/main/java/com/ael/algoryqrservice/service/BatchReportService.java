package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.AiServiceClient;
import com.ael.algoryqrservice.client.dto.AiBatchReportClientDtos;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.BatchReport;
import com.ael.algoryqrservice.model.BatchReportItem;
import com.ael.algoryqrservice.model.dto.BatchReportDtos;
import com.ael.algoryqrservice.repository.BatchReportItemRepository;
import com.ael.algoryqrservice.repository.BatchReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchReportService {

    private static final Set<String> OPEN_STATUSES = Set.of(
            BatchReport.STATUS_PENDING,
            BatchReport.STATUS_VALIDATING,
            BatchReport.STATUS_IN_PROGRESS,
            BatchReport.STATUS_FINALIZING
    );

    private final AiServiceClient aiServiceClient;
    private final BatchReportRepository batchReportRepository;
    private final BatchReportItemRepository batchReportItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BatchReport createSubmittedBatch(Long userId, List<BatchReportItemDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items are required");
        }

        List<AiBatchReportClientDtos.Item> aiItems = drafts.stream()
                .map(draft -> AiBatchReportClientDtos.Item.builder()
                        .customId(draft.id().toString())
                        .payload(draft.payload())
                        .build())
                .toList();

        AiBatchReportClientDtos.CreateResponse created = aiServiceClient.createBatchReport(
                AiBatchReportClientDtos.CreateRequest.builder().items(aiItems).build()
        );
        if (created == null || created.getOpenaiBatchId() == null || created.getOpenaiBatchId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai-service did not return openaiBatchId");
        }

        UUID batchId = UUID.randomUUID();
        BatchReport batch = BatchReport.builder()
                .id(batchId)
                .userId(userId)
                .openaiBatchId(created.getOpenaiBatchId())
                .status(BatchReport.STATUS_PENDING)
                .requestTotal(drafts.size())
                .requestCompleted(0)
                .requestFailed(0)
                .build();
        batchReportRepository.save(batch);

        for (BatchReportItemDraft draft : drafts) {
            batchReportItemRepository.save(BatchReportItem.builder()
                    .id(draft.id())
                    .batchId(batchId)
                    .status(BatchReportItem.STATUS_PENDING)
                    .payloadJson(draft.payload())
                    .branchId(draft.branchId())
                    .branchName(draft.branchName())
                    .menuId(draft.menuId())
                    .menuName(draft.menuName())
                    .fromDate(draft.from())
                    .toDate(draft.to())
                    .locale(draft.locale())
                    .build());
        }

        log.info(
                "Batch report created. batchId={} openaiBatchId={} userId={} itemCount={}",
                batchId,
                created.getOpenaiBatchId(),
                userId,
                drafts.size()
        );
        return batch;
    }

    @Transactional(readOnly = true)
    public Page<BatchReportDtos.BatchReportListItem> listReports(Long userId, Pageable pageable) {
        return batchReportRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public BatchReportDtos.BatchReportDetail getReport(Long userId, UUID batchId) {
        BatchReport batch = batchReportRepository.findByIdAndUserId(batchId, userId)
                .orElseThrow(() -> new NotFoundException("Rapor bulunamadi: " + batchId));
        List<BatchReportItem> items = batchReportItemRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        return toDetail(batch, items);
    }

    @Transactional(readOnly = true)
    public List<BatchReport> findOpenBatches() {
        return batchReportRepository.findByStatusInOrderByUpdatedAtAsc(OPEN_STATUSES);
    }

    @Transactional
    public ReconcileOutcome reconcileOne(UUID batchId) {
        BatchReport batch = batchReportRepository.findById(batchId).orElse(null);
        if (batch == null || !OPEN_STATUSES.contains(normalizeStatus(batch.getStatus()))) {
            return ReconcileOutcome.empty();
        }

        AiBatchReportClientDtos.StatusResponse remote = aiServiceClient.getBatchReportStatus(batch.getOpenaiBatchId());
        if (remote == null || remote.getStatus() == null || remote.getStatus().isBlank()) {
            return ReconcileOutcome.empty();
        }

        String remoteStatus = normalizeStatus(remote.getStatus());
        AiBatchReportClientDtos.RequestCounts counts = remote.getRequestCounts();
        int total = counts == null || counts.getTotal() == null ? batch.getRequestTotal() : counts.getTotal();
        int completed = counts == null || counts.getCompleted() == null ? 0 : counts.getCompleted();
        int failed = counts == null || counts.getFailed() == null ? 0 : counts.getFailed();

        boolean changed = !Objects.equals(normalizeStatus(batch.getStatus()), remoteStatus)
                || !Objects.equals(batch.getRequestTotal(), total)
                || !Objects.equals(batch.getRequestCompleted(), completed)
                || !Objects.equals(batch.getRequestFailed(), failed);

        if (changed) {
            batch.setStatus(remoteStatus);
            batch.setRequestTotal(total);
            batch.setRequestCompleted(completed);
            batch.setRequestFailed(failed);
            if (isTerminal(remoteStatus) && batch.getCompletedAt() == null) {
                batch.setCompletedAt(LocalDateTime.now());
            }
            batchReportRepository.save(batch);
        }

        List<ItemOutcome> itemOutcomes = new ArrayList<>();
        if (BatchReport.STATUS_COMPLETED.equals(remoteStatus)) {
            itemOutcomes.addAll(applyResults(batch));
        } else if (isFailedTerminal(remoteStatus)) {
            itemOutcomes.addAll(markItemsFailed(batch, "OpenAI batch status: " + remoteStatus));
        } else if (changed && isProcessing(remoteStatus)) {
            for (BatchReportItem item : batchReportItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
                if (BatchReportItem.STATUS_PENDING.equals(normalizeStatus(item.getStatus()))) {
                    itemOutcomes.add(ItemOutcome.processing(item.getId()));
                }
            }
        }

        return new ReconcileOutcome(batch.getId(), remoteStatus, itemOutcomes);
    }

    private List<ItemOutcome> applyResults(BatchReport batch) {
        List<AiBatchReportClientDtos.ResultItem> results =
                aiServiceClient.getBatchReportResults(batch.getOpenaiBatchId());
        Map<UUID, BatchReportItem> itemsById = new HashMap<>();
        for (BatchReportItem item : batchReportItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
            itemsById.put(item.getId(), item);
        }

        List<ItemOutcome> outcomes = new ArrayList<>();
        for (AiBatchReportClientDtos.ResultItem result : results) {
            if (result.getCustomId() == null || result.getCustomId().isBlank()) {
                continue;
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(result.getCustomId());
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown batch report custom_id={}", result.getCustomId());
                continue;
            }
            BatchReportItem item = itemsById.get(itemId);
            if (item == null) {
                continue;
            }
            String itemStatus = normalizeStatus(result.getStatus());
            if (BatchReportItem.STATUS_COMPLETED.equals(itemStatus)) {
                Map<String, Object> body = jsonNodeToMap(result.getBody());
                item.setStatus(BatchReportItem.STATUS_COMPLETED);
                item.setResultJson(body);
                item.setErrorMessage(null);
                batchReportItemRepository.save(item);
                outcomes.add(ItemOutcome.completed(item.getId(), extractResultText(body)));
            } else {
                String errorMessage = stringifyError(result.getError());
                item.setStatus(BatchReportItem.STATUS_FAILED);
                item.setErrorMessage(errorMessage);
                batchReportItemRepository.save(item);
                outcomes.add(ItemOutcome.failed(item.getId(), "BATCH_ITEM_FAILED", errorMessage));
            }
        }

        for (BatchReportItem item : itemsById.values()) {
            if (BatchReportItem.STATUS_PENDING.equals(normalizeStatus(item.getStatus()))) {
                item.setStatus(BatchReportItem.STATUS_FAILED);
                item.setErrorMessage("Missing result in OpenAI batch output");
                batchReportItemRepository.save(item);
                outcomes.add(ItemOutcome.failed(item.getId(), "BATCH_ITEM_MISSING", item.getErrorMessage()));
            }
        }
        return outcomes;
    }

    private List<ItemOutcome> markItemsFailed(BatchReport batch, String message) {
        List<ItemOutcome> outcomes = new ArrayList<>();
        for (BatchReportItem item : batchReportItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
            if (!BatchReportItem.STATUS_PENDING.equals(normalizeStatus(item.getStatus()))) {
                continue;
            }
            item.setStatus(BatchReportItem.STATUS_FAILED);
            item.setErrorMessage(message);
            batchReportItemRepository.save(item);
            outcomes.add(ItemOutcome.failed(item.getId(), "BATCH_FAILED", message));
        }
        return outcomes;
    }

    private BatchReportDtos.BatchReportListItem toListItem(BatchReport batch) {
        return new BatchReportDtos.BatchReportListItem(
                batch.getId(),
                batch.getOpenaiBatchId(),
                batch.getStatus(),
                new BatchReportDtos.RequestCounts(
                        nullSafe(batch.getRequestTotal()),
                        nullSafe(batch.getRequestCompleted()),
                        nullSafe(batch.getRequestFailed())
                ),
                batch.getCreatedAt(),
                batch.getCompletedAt()
        );
    }

    private BatchReportDtos.BatchReportDetail toDetail(BatchReport batch, List<BatchReportItem> items) {
        return new BatchReportDtos.BatchReportDetail(
                batch.getId(),
                batch.getOpenaiBatchId(),
                batch.getStatus(),
                new BatchReportDtos.RequestCounts(
                        nullSafe(batch.getRequestTotal()),
                        nullSafe(batch.getRequestCompleted()),
                        nullSafe(batch.getRequestFailed())
                ),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getCompletedAt(),
                items.stream().map(this::toItemDetail).toList()
        );
    }

    private BatchReportDtos.BatchReportItemDetail toItemDetail(BatchReportItem item) {
        return new BatchReportDtos.BatchReportItemDetail(
                item.getId(),
                item.getStatus(),
                item.getBranchId(),
                item.getBranchName(),
                item.getMenuId(),
                item.getMenuName(),
                item.getFromDate(),
                item.getToDate(),
                item.getLocale(),
                item.getResultJson(),
                item.getErrorMessage(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private String extractResultText(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object outputText = body.get("output_text");
        if (outputText instanceof String text && !text.isBlank()) {
            return text;
        }
        Object output = body.get("output");
        if (output instanceof List<?> chunks) {
            StringBuilder sb = new StringBuilder();
            for (Object chunk : chunks) {
                if (!(chunk instanceof Map<?, ?> map)) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List<?> contentList)) {
                    continue;
                }
                for (Object part : contentList) {
                    if (part instanceof Map<?, ?> partMap) {
                        Object text = partMap.get("text");
                        if (text != null) {
                            sb.append(text);
                        }
                    }
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return body.toString();
        }
    }

    private String stringifyError(JsonNode error) {
        if (error == null || error.isNull()) {
            return "Batch item failed";
        }
        if (error.isTextual()) {
            return error.asText();
        }
        return error.toString();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isTerminal(String status) {
        return BatchReport.STATUS_COMPLETED.equals(status) || isFailedTerminal(status);
    }

    private static boolean isFailedTerminal(String status) {
        return BatchReport.STATUS_FAILED.equals(status)
                || BatchReport.STATUS_EXPIRED.equals(status)
                || BatchReport.STATUS_CANCELLED.equals(status);
    }

    private static boolean isProcessing(String status) {
        return BatchReport.STATUS_VALIDATING.equals(status)
                || BatchReport.STATUS_IN_PROGRESS.equals(status)
                || BatchReport.STATUS_FINALIZING.equals(status);
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    public record BatchReportItemDraft(
            UUID id,
            Map<String, Object> payload,
            Long branchId,
            String branchName,
            Long menuId,
            String menuName,
            LocalDate from,
            LocalDate to,
            String locale
    ) {
    }

    public record ReconcileOutcome(
            UUID batchId,
            String status,
            List<ItemOutcome> items
    ) {
        static ReconcileOutcome empty() {
            return new ReconcileOutcome(null, null, List.of());
        }
    }

    public record ItemOutcome(
            UUID itemId,
            String status,
            String errorCode,
            String errorMessage,
            String resultText
    ) {
        static ItemOutcome processing(UUID itemId) {
            return new ItemOutcome(itemId, BatchReportItem.STATUS_PENDING, null, null, null);
        }

        static ItemOutcome completed(UUID itemId, String resultText) {
            return new ItemOutcome(itemId, BatchReportItem.STATUS_COMPLETED, null, null, resultText);
        }

        static ItemOutcome failed(UUID itemId, String errorCode, String errorMessage) {
            return new ItemOutcome(itemId, BatchReportItem.STATUS_FAILED, errorCode, errorMessage, null);
        }
    }
}
