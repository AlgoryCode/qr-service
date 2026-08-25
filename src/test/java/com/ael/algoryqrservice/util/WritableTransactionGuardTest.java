package com.ael.algoryqrservice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class WritableTransactionGuardTest {

    private final WritableTransactionGuard writableTransactionGuard = new WritableTransactionGuard();

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void allowsWrites_whenTransactionIsWritable_thenAllow() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThat(writableTransactionGuard.allowsWrites("entitlement repair", 7L)).isTrue();
    }

    @Test
    void allowsWrites_whenTransactionIsReadOnly_thenDeny() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(writableTransactionGuard.allowsWrites("entitlement repair", 7L)).isFalse();
    }
}
