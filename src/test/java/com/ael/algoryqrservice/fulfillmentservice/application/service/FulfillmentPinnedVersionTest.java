package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PlanVersionPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PurchaseModulePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Renewals grant the package version the purchase was bought on, not the package as edited since. */
class FulfillmentPinnedVersionTest {

    private final FulfillmentRepositoryPort fulfillments = mock(FulfillmentRepositoryPort.class);
    private final PackageCatalogPort catalog = mock(PackageCatalogPort.class);
    private final EntitlementPort entitlements = mock(EntitlementPort.class);
    private final PurchaseModulePort modules = mock(PurchaseModulePort.class);
    private final PlanVersionPort versions = mock(PlanVersionPort.class);
    private final FulfillmentServiceImpl service = new FulfillmentServiceImpl(
            fulfillments, catalog, entitlements, modules, versions, mock(SubscriptionEvents.class));

    @BeforeEach
    void setUp() {
        when(fulfillments.findByEventId(any())).thenReturn(Optional.empty());
        when(fulfillments.saveFulfillment(any())).thenAnswer(invocation -> {
            PackageFulfillment f = invocation.getArgument(0);
            return new PackageFulfillment(7L, f.eventId(), f.eventType(), f.purchaseId(), f.merchantId(), f.packageId(),
                    f.packageCode(), null, null, null, null, f.periodStart(), f.periodEnd(), f.amount(), f.currency(),
                    f.status(), f.createdAt(), f.updatedAt());
        });
        when(fulfillments.findActiveByMerchantId(1L)).thenReturn(Optional.empty());
        when(entitlements.lockActiveByMerchantId(1L)).thenReturn(List.of());
        when(entitlements.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modules.modulesOf(any())).thenReturn(List.of());
        // The package today grants 60 products ...
        when(catalog.findById(30L)).thenReturn(Optional.of(new PlanPackage(30L, "STARTER_PACKAGE", "Starter", null,
                PackageKind.CATALOG, List.of(), BigDecimal.TEN, null, BigDecimal.TEN, BigDecimal.ZERO, "TRY", true, 30, 0,
                true, false, List.of(line(1L, "MENU_PRODUCT", 60)), Instant.now(), Instant.now())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void renewalGrantsTheVersionThePurchaseWasBoughtOn() {
        // ... but this subscriber bought version 1, which granted 50.
        when(versions.linesOfPurchase(500L)).thenReturn(Optional.of(List.of(line(9L, "MENU_PRODUCT", 50))));

        service.fulfillPaidPackage(command(500L));

        ArgumentCaptor<List<UserEntitlement>> saved = ArgumentCaptor.forClass(List.class);
        verify(entitlements).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(row -> assertThat(row.quantity()).isEqualTo(50));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unpinnedPurchaseGrantsTheCurrentPackage() {
        when(versions.linesOfPurchase(500L)).thenReturn(Optional.empty());

        service.fulfillPaidPackage(command(500L));

        ArgumentCaptor<List<UserEntitlement>> saved = ArgumentCaptor.forClass(List.class);
        verify(entitlements).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(row -> assertThat(row.quantity()).isEqualTo(60));
    }

    private static PlanPackageItem line(Long id, String code, int quantity) {
        return new PlanPackageItem(id, 88L, code, code, code + "_OWNER", ProductType.PACKAGE_PRODUCT, quantity, false);
    }

    private static FulfillmentCommand command(Long purchaseId) {
        LocalDate start = LocalDate.now();
        return new FulfillmentCommand("evt-" + purchaseId, "payment.completed", purchaseId, 1L, 30L, "STARTER_PACKAGE",
                null, null, null, 1, start, start.plusDays(30), BigDecimal.TEN, "TRY", Map.of());
    }
}
