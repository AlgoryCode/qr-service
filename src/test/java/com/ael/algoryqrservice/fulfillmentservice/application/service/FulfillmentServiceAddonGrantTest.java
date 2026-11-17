package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PurchaseModulePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FulfillmentServiceAddonGrantTest {

    private final FulfillmentRepositoryPort fulfillments = mock(FulfillmentRepositoryPort.class);
    private final PackageCatalogPort catalog = mock(PackageCatalogPort.class);
    private final EntitlementPort entitlements = mock(EntitlementPort.class);
    private final FulfillmentServiceImpl service = new FulfillmentServiceImpl(
            fulfillments, catalog, entitlements, mock(PurchaseModulePort.class),
            mock(com.ael.algoryqrservice.fulfillmentservice.domain.port.PlanVersionPort.class), mock(SubscriptionEvents.class));

    private static final AddonGrant SMART_REPORTS = new AddonGrant(1L, 900L, List.of(
            new AddonGrant.Line(95L, "SMART_REPORTING", "SMART_REPORTING_OWNER", 5, null)));

    @Test
    @SuppressWarnings("unchecked")
    void paidAddonBecomesSpendableAddonEntitlementOnTheHostPackage() {
        when(fulfillments.findActiveByMerchantId(1L)).thenReturn(Optional.of(new ActivePackage(
                1L, 1L, 7L, 376L, 32L, "ULTIMATE_PACKAGE", null, null, PackageStatus.ACTIVE, Instant.now(), Instant.now(),
                SubscriptionKind.PAID, null)));
        when(entitlements.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.grantAddon(SMART_REPORTS);

        ArgumentCaptor<List<UserEntitlement>> saved = ArgumentCaptor.forClass(List.class);
        verify(entitlements).saveAll(saved.capture());
        UserEntitlement row = saved.getValue().getFirst();
        assertThat(row.fulfillmentId()).isEqualTo(7L);
        assertThat(row.purchaseId()).isEqualTo(900L);
        assertThat(row.productId()).isEqualTo(95L);
        assertThat(row.productTypeId()).isEqualTo(ProductType.ADDON_PRODUCT);
        assertThat(row.featureCode()).isEqualTo("SMART_REPORTING");
        assertThat(row.quantity()).isEqualTo(5);
        assertThat(row.usedQuantity()).isZero();
        assertThat(row.unlimited()).isFalse();
        assertThat(row.source()).isEqualTo(EntitlementSource.ADDON_PURCHASE);
        assertThat(row.status()).isEqualTo(PackageStatus.ACTIVE);
        assertThat(row.expiresAt()).isNull();
        // Never touches the package: no new fulfillment, nothing superseded.
        verify(fulfillments, never()).saveFulfillment(any());
        verify(entitlements, never()).supersedeOtherEntitlements(any(), any());
    }

    @Test
    void replayedPaymentDoesNotGrantTwice() {
        when(entitlements.existsByPurchaseIdAndSource(900L, EntitlementSource.ADDON_PURCHASE)).thenReturn(true);

        assertThat(service.grantAddon(SMART_REPORTS)).isEmpty();

        verify(entitlements, never()).saveAll(anyList());
    }

    @Test
    void refundCancelsTheAddonEntitlements() {
        when(entitlements.changeStatusByPurchaseId(900L, EntitlementSource.ADDON_PURCHASE, PackageStatus.CANCELLED))
                .thenReturn(1);

        assertThat(service.revokeAddon(900L)).isEqualTo(1);
    }
}
