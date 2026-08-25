package com.ael.algoryqrservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Guards opportunistic self-healing writes that may be reached from a read-only transaction.
 *
 * <p>Postgres rejects both updates and locking selects inside a read-only transaction with
 * {@code cannot execute SELECT FOR NO KEY UPDATE in a read-only transaction}. Repair work is
 * never essential for the current response, so it is skipped instead of failing the request.
 */
@Component
@Slf4j
public class WritableTransactionGuard {

    /**
     * @return {@code true} when the caller may perform writes in the current transaction.
     */
    public boolean allowsWrites(String operation, Long userId) {
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return true;
        }
        log.warn("Skipping {} for userId={}: current transaction is read-only.", operation, userId);
        return false;
    }
}
