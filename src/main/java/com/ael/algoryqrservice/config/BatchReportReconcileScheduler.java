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

    @Scheduled(fixedDelayString = "${batch-reports.reconcile-interval-ms:120000}")
    public void reconcile() {
        for (BatchReport batch : batchReportService.findOpenBatches()) {
            try {
                BatchReportService.ReconcileOutcome outcome = batchReportService.reconcileOne(batch.getId());
                applySmartReportOutcomes(outcome);
            } catch (Exception ex) {
                log.warn(
                        "Batch report reconcile failed. batchId={} openaiBatchId={} error={}",
                        batch.getId(),
                        batch.getOpenaiBatchId(),
                        ex.getMessage()
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
