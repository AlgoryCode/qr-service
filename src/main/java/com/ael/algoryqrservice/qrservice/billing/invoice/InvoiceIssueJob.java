package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceIssueJob {

    private static final int BATCH = 50;

    private final InvoiceService invoiceService;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelayString = "${billing.invoice.fixed-delay:120000}", initialDelay = 90_000)
    public void run() {
        schedulerLock.runExclusively("invoice-issue", Duration.ofMinutes(10), () -> {
            int issued = invoiceService.issuePending(BATCH);
            if (issued > 0) {
                log.info("invoice_issue_batch issued={}", issued);
            }
        });
    }
}
