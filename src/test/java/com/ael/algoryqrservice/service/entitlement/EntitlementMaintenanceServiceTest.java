package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.FulfillmentMigrationService;
import com.ael.algoryqrservice.util.WritableTransactionGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementMaintenanceServiceTest {

    private static final Long USER_ID = 9L;

    @Mock
    private PurchaseSelectionPolicy selectionPolicy;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private PackageEntitlementWriter entitlementWriter;
    @Mock
    private FulfillmentGrantService fulfillmentGrantService;
    @Mock
    private FulfillmentMigrationService fulfillmentMigrationService;
    @Mock
    private FeatureUsageSyncRegistry usageSyncRegistry;
    @Mock
    private WritableTransactionGuard writableTransactionGuard;

    @InjectMocks
    private EntitlementMaintenanceService maintenanceService;

    @Test
    void repairUser_whenTransactionIsReadOnly_thenSkipEveryWrite() {
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(false);

        maintenanceService.repairUser(USER_ID);

        verifyNoInteractions(selectionPolicy, entitlementWriter, fulfillmentMigrationService, usageSyncRegistry);
    }

    @Test
    void repairUser_whenSubscriptionHasPackage_thenRealignEntitlementsAndSyncUsage() {
        Purchase subscription = Purchase.builder()
                .id(10L)
                .userId(USER_ID)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
        PlanPackage planPackage = PlanPackage.builder().id(4L).priority(300).build();

        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);
        when(selectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of(subscription));
        when(selectionPolicy.usableAddons(USER_ID)).thenReturn(List.of());
        when(planPackageRepository.findAllByIdWithItems(List.of(4L))).thenReturn(List.of(planPackage));

        maintenanceService.repairUser(USER_ID);

        verify(entitlementWriter).ensureEntitlementsForPackage(subscription, planPackage);
        verify(fulfillmentGrantService).repairAddonFulfillmentsForUser(USER_ID);
        verify(fulfillmentMigrationService).backfillUser(USER_ID);
        verify(usageSyncRegistry).synchronizeAll(USER_ID);
    }

    @Test
    void repairUser_whenNoSubscriptions_thenSkipPackageLookup() {
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);
        when(selectionPolicy.usableSubscriptions(USER_ID)).thenReturn(List.of());
        when(selectionPolicy.usableAddons(USER_ID)).thenReturn(List.of());

        maintenanceService.repairUser(USER_ID);

        verify(planPackageRepository, never()).findAllByIdWithItems(any());
        verify(usageSyncRegistry).synchronizeAll(USER_ID);
    }
}
