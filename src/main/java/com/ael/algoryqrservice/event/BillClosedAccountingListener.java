package com.ael.algoryqrservice.event;

import com.ael.algoryqrservice.service.UserAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillClosedAccountingListener {

    private final UserAccountingService userAccountingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillClosed(BillClosedEvent event) {
        try {
            userAccountingService.recordBillSale(event.billId());
        } catch (Exception ex) {
            log.error("Failed to record accounting BILL_SALE for billId={}", event.billId(), ex);
        }
    }
}
