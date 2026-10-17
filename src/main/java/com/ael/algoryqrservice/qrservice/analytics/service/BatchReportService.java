package com.ael.algoryqrservice.qrservice.analytics.service;

import com.ael.algoryqrservice.qrservice.client.AiServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.AiBatchReportClientDtos;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.BatchReport;
import com.ael.algoryqrservice.qrservice.model.BatchReportItem;
import com.ael.algoryqrservice.qrservice.analytics.dto.BatchReportDtos;
import com.ael.algoryqrservice.qrservice.repository.BatchReportItemRepository;
import com.ael.algoryqrservice.qrservice.repository.BatchReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    public BatchReport createSubmittedBatch(Long merchantId, List<BatchReportItemDraft> drafts) {
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

        return transactionTemplate.execute(status -> persistCreatedBatch(merchantId, drafts, created.getOpenaiBatchId()));
    }

    private BatchReport persistCreatedBatch(
            Long merchantId,
            List<BatchReportItemDraft> drafts,
            String openaiBatchId
    ) {
        UUID batchId = UUID.randomUUID();
        BatchReport batch = BatchReport.builder()
                .id(batchId)
                .merchantId(merchantId)
                .openaiBatchId(openaiBatchId)
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
                "Batch report created. batchId={} openaiBatchId={} merchantId={} itemCount={}",
                batchId,
                openaiBatchId,
                merchantId,
                drafts.size()
        );
        return batch;
    }

    @Transactional(readOnly = true)
    public Page<BatchReportDtos.BatchReportListItem> listReports(Long merchantId, Pageable pageable) {
        return batchReportRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public BatchReportDtos.BatchReportDetail getReport(Long merchantId, UUID batchId) {
        BatchReport batch = batchReportRepository.findByIdAndMerchantId(batchId, merchantId)
                .orElseThrow(() -> new NotFoundException("Rapor bulunamadi: " + batchId));
        List<BatchReportItem> items = batchReportItemRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        return toDetail(batch, items);
    }

    @Transactional(readOnly = true)
    public List<BatchReport> findOpenBatches() {
        return batchReportRepository.findByStatusInOrderByUpdatedAtAsc(OPEN_STATUSES);
    }

    public ReconcileOutcome reconcileOne(UUID batchId) {
        BatchSnapshot snapshot = transactionTemplate.execute(status -> loadOpenBatchSnapshot(batchId));
        if (snapshot == null) {
            return ReconcileOutcome.empty();
        }

        AiBatchReportClientDtos.StatusResponse remote;
        try {
            remote = aiServiceClient.getBatchReportStatus(snapshot.openaiBatchId());
        } catch (Exception ex) {
            log.warn(
                    "Batch report status call failed. batchId={} openaiBatchId={} error={}",
                    snapshot.batchId(),
                    snapshot.openaiBatchId(),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
        if (remote == null || remote.getStatus() == null || remote.getStatus().isBlank()) {
            log.warn(
                    "Batch report status empty. batchId={} openaiBatchId={}",
                    snapshot.batchId(),
                    snapshot.openaiBatchId()
            );
            return ReconcileOutcome.empty();
        }

        String remoteStatus = normalizeStatus(remote.getStatus());
        AiBatchReportClientDtos.RequestCounts counts = remote.getRequestCounts();
        int total = counts == null || counts.getTotal() == null ? snapshot.requestTotal() : counts.getTotal();
        int completed = counts == null || counts.getCompleted() == null ? 0 : counts.getCompleted();
        int failed = counts == null || counts.getFailed() == null ? 0 : counts.getFailed();
        log.info(
                "Batch report remote status. batchId={} openaiBatchId={} localStatus={} remoteStatus={} total={} completed={} failed={}",
                snapshot.batchId(),
                snapshot.openaiBatchId(),
                snapshot.localStatus(),
                remoteStatus,
                total,
                completed,
                failed
        );

        List<AiBatchReportClientDtos.ResultItem> remoteResults = List.of();
        if (BatchReport.STATUS_COMPLETED.equals(remoteStatus)) {
            try {
                remoteResults = aiServiceClient.getBatchReportResults(snapshot.openaiBatchId());
                log.info(
                        "Batch report results fetched. batchId={} openaiBatchId={} resultCount={}",
                        snapshot.batchId(),
                        snapshot.openaiBatchId(),
                        remoteResults.size()
                );
            } catch (Exception ex) {
                log.warn(
                        "Batch report results call failed. batchId={} openaiBatchId={} error={}",
                        snapshot.batchId(),
                        snapshot.openaiBatchId(),
                        ex.getMessage(),
                        ex
                );
                if (isUnrecoverableBatchResultsError(ex)) {
                    String message = "OpenAI batch output unavailable: " + summarizeExceptionMessage(ex);
                    ReconcileOutcome failedOutcome = transactionTemplate.execute(status ->
                            persistUnrecoverableResultsFailure(
                                    snapshot.batchId(),
                                    total,
                                    completed,
                                    failed,
                                    message
                            ));
                    return failedOutcome == null ? ReconcileOutcome.empty() : failedOutcome;
                }
                throw ex;
            }
        }

        List<AiBatchReportClientDtos.ResultItem> resultsForPersist = remoteResults;
        ReconcileOutcome outcome = transactionTemplate.execute(status -> persistRemoteState(
                snapshot.batchId(),
                remoteStatus,
                total,
                completed,
                failed,
                resultsForPersist
        ));
        return outcome == null ? ReconcileOutcome.empty() : outcome;
    }

    private BatchSnapshot loadOpenBatchSnapshot(UUID batchId) {
        BatchReport batch = batchReportRepository.findById(batchId).orElse(null);
        if (batch == null || !OPEN_STATUSES.contains(normalizeStatus(batch.getStatus()))) {
            return null;
        }
        return new BatchSnapshot(
                batch.getId(),
                batch.getOpenaiBatchId(),
                normalizeStatus(batch.getStatus()),
                nullSafe(batch.getRequestTotal())
        );
    }

    private ReconcileOutcome persistRemoteState(
            UUID batchId,
            String remoteStatus,
            int total,
            int completed,
            int failed,
            List<AiBatchReportClientDtos.ResultItem> remoteResults
    ) {
        BatchReport batch = batchReportRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return ReconcileOutcome.empty();
        }

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
            log.info(
                    "Batch report local status updated. batchId={} status={} total={} completed={} failed={}",
                    batch.getId(),
                    remoteStatus,
                    total,
                    completed,
                    failed
            );
        }

        List<ItemOutcome> itemOutcomes = new ArrayList<>();
        if (BatchReport.STATUS_COMPLETED.equals(remoteStatus)) {
            itemOutcomes.addAll(applyResults(batch, remoteResults));
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

    private ReconcileOutcome persistUnrecoverableResultsFailure(
            UUID batchId,
            int total,
            int completed,
            int failed,
            String message
    ) {
        BatchReport batch = batchReportRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return ReconcileOutcome.empty();
        }
        batch.setStatus(BatchReport.STATUS_FAILED);
        batch.setRequestTotal(total);
        batch.setRequestCompleted(completed);
        batch.setRequestFailed(failed);
        batch.setErrorMessage(message);
        if (batch.getCompletedAt() == null) {
            batch.setCompletedAt(LocalDateTime.now());
        }
        batchReportRepository.save(batch);
        log.warn(
                "Batch report marked failed (output unavailable). batchId={} openaiBatchId={} message={}",
                batch.getId(),
                batch.getOpenaiBatchId(),
                message
        );
        List<ItemOutcome> itemOutcomes = markItemsFailed(batch, message);
        return new ReconcileOutcome(batch.getId(), BatchReport.STATUS_FAILED, itemOutcomes);
    }

    private List<ItemOutcome> applyResults(
            BatchReport batch,
            List<AiBatchReportClientDtos.ResultItem> results
    ) {
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
                Map<String, Object> body = result.getBody() != null ? result.getBody() : Map.of();
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

    private String stringifyError(Object error) {
        if (error == null) {
            return "Batch item failed";
        }
        if (error instanceof String text && !text.isBlank()) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception ex) {
            return error.toString();
        }
    }

    private static boolean isUnrecoverableBatchResultsError(Exception ex) {
        String message = summarizeExceptionMessage(ex).toLowerCase(Locale.ROOT);
        return message.contains("no such file object")
                || message.contains("failed to retrieve batch results");
    }

    private static String summarizeExceptionMessage(Exception ex) {
        if (ex instanceof HttpStatusCodeException httpEx) {
            String body = httpEx.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                return body;
            }
        }
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
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

    private record BatchSnapshot(
            UUID batchId,
            String openaiBatchId,
            String localStatus,
            int requestTotal
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
