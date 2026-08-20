package com.ael.algoryqrservice.event;

import com.ael.algoryqrservice.service.WaiterCommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillClosedEventListener {

    private final WaiterCommissionService waiterCommissionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillClosed(BillClosedEvent event) {
        if (event == null || event.billId() == null) {
            return;
        }
        try {
            waiterCommissionService.settleBillCommissions(event.billId());
        } catch (Exception ex) {
            log.error("Bill commission settle failed for billId={}", event.billId(), ex);
        }
    }
}
