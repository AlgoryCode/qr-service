package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.model.BatchReport;
import com.ael.algoryqrservice.model.BatchReportItem;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.service.BatchReportService;
import com.ael.algoryqrservice.service.SmartReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchReportReconcileScheduler {

    private final BatchReportService batchReportService;
    private final SmartReportService smartReportService;

    @Scheduled(fixedDelayString = "${batch-reports.reconcile-interval-ms:60000}")
    public void reconcile() {
        var openBatches = batchReportService.findOpenBatches();
        log.info("Batch report reconcile tick. openBatchCount={}", openBatches.size());
        for (BatchReport batch : openBatches) {
            try {
                log.info(
                        "Batch report reconcile start. batchId={} openaiBatchId={} localStatus={}",
                        batch.getId(),
                        batch.getOpenaiBatchId(),
                        batch.getStatus()
                );
                BatchReportService.ReconcileOutcome outcome = batchReportService.reconcileOne(batch.getId());
                log.info(
                        "Batch report reconcile done. batchId={} remoteStatus={} itemOutcomes={}",
                        batch.getId(),
                        outcome == null ? null : outcome.status(),
                        outcome == null || outcome.items() == null ? 0 : outcome.items().size()
                );
                applySmartReportOutcomes(outcome);
            } catch (Exception ex) {
                log.warn(
                        "Batch report reconcile failed. batchId={} openaiBatchId={} error={}",
                        batch.getId(),
                        batch.getOpenaiBatchId(),
                        ex.getMessage(),
                        ex
                );
            }
        }
    }

    private void applySmartReportOutcomes(BatchReportService.ReconcileOutcome outcome) {
        if (outcome == null || outcome.items() == null || outcome.items().isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (BatchReportService.ItemOutcome item : outcome.items()) {
            if (item.itemId() == null) {
                continue;
            }
            if (BatchReportItem.STATUS_COMPLETED.equals(item.status())) {
                log.info(
                        "Batch report applying smart-report completed. itemId={} resultChars={}",
                        item.itemId(),
                        item.resultText() == null ? 0 : item.resultText().length()
                );
                smartReportService.applyStatusEvent(new SmartReportStatusMessage(
                        item.itemId(),
                        SmartReportEvent.STATUS_COMPLETED,
                        null,
                        item.resultText(),
                        null,
                        null,
                        now,
                        now
                ));
                continue;
            }
            if (BatchReportItem.STATUS_FAILED.equals(item.status())) {
                log.warn(
                        "Batch report applying smart-report failed. itemId={} code={} message={}",
                        item.itemId(),
                        item.errorCode(),
                        item.errorMessage()
                );
                smartReportService.applyStatusEvent(new SmartReportStatusMessage(
                        item.itemId(),
                        SmartReportEvent.STATUS_FAILED,
                        null,
                        null,
                        item.errorCode(),
                        item.errorMessage(),
                        now,
                        now
                ));
                continue;
            }
            log.info("Batch report applying smart-report processing. itemId={}", item.itemId());
            smartReportService.applyStatusEvent(new SmartReportStatusMessage(
                    item.itemId(),
                    SmartReportEvent.STATUS_PROCESSING,
                    null,
                    null,
                    null,
                    null,
                    now,
                    null
            ));
        }
    }
}
