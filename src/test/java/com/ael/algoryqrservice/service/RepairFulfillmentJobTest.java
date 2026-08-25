package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairFulfillmentJobTest {

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentMigrationService fulfillmentMigrationService;

    private RepairFulfillmentJob job;

    @BeforeEach
    void setUp() {
        job = new RepairFulfillmentJob(
                purchaseRepository,
                grantFulfillmentRepository,
                fulfillmentMigrationService,
                new AppProperties()
        );
    }

    @Test
    void repairForPurchase_whenUserIdNull_thenSkipBackfill() {
        Purchase purchase = Purchase.builder()
                .id(9L)
                .userId(null)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        when(purchaseRepository.findById(9L)).thenReturn(Optional.of(purchase));

        job.repairForPurchase(9L);

        verify(fulfillmentMigrationService, never()).backfillUser(any());
    }
}
