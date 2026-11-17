package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.QuotaExceededException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.CatalogProduct;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.ProductPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementUsageServiceTest {

    private static final long MERCHANT = 1L;
    private static final CatalogProduct ADDON =
            new CatalogProduct(95L, "SMART_REPORTING_ADDON", "Ek Akilli Rapor", "SMART_REPORTING", "SMART_REPORTING_OWNER");

    private final FulfillmentRepositoryPort fulfillments = mock(FulfillmentRepositoryPort.class);
    private final EntitlementPort entitlements = mock(EntitlementPort.class);
    private final ProductPort products = mock(ProductPort.class);
    private final com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService snapshots =
            mock(com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService.class);
    private final com.ael.algoryqrservice.fulfillmentservice.domain.port.CountedResourcePort counted =
            mock(com.ael.algoryqrservice.fulfillmentservice.domain.port.CountedResourcePort.class);
    private final EntitlementUsageService service = new EntitlementUsageService(fulfillments, entitlements, products, counted, snapshots);

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        when(fulfillments.findActiveByMerchantId(MERCHANT)).thenReturn(Optional.of(new ActivePackage(
                1L, MERCHANT, 7L, 376L, 32L, "ULTIMATE_PACKAGE", null, null, PackageStatus.ACTIVE, now, now,
                SubscriptionKind.PAID, null)));
        when(products.findByCode("SMART_REPORTING_ADDON")).thenReturn(Optional.of(ADDON));
        when(entitlements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void addonConsumptionSpendsTheAddonNotThePackagesUnlimitedFeature() {
        UserEntitlement packageRow = entitlement(64L, 86L, 0, true, 0, EntitlementSource.PACKAGE_INCLUDE);
        UserEntitlement addonRow = entitlement(144L, 95L, 10, false, 0, EntitlementSource.ADDON_PURCHASE);
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(packageRow, addonRow));

        UserEntitlement spent = service.consume(MERCHANT, "SMART_REPORTING_ADDON", 1);

        assertThat(spent.id()).isEqualTo(144L);
        assertThat(spent.usedQuantity()).isEqualTo(1);
    }

    @Test
    void consumptionMovesToTheNextAddonOnceOneIsUsedUp() {
        UserEntitlement first = entitlement(1L, 95L, 2, false, 2, EntitlementSource.ADDON_PURCHASE);
        UserEntitlement second = entitlement(2L, 95L, 5, false, 0, EntitlementSource.ADDON_PURCHASE);
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(first, second));

        service.consume(MERCHANT, "SMART_REPORTING_ADDON", 1);

        ArgumentCaptor<UserEntitlement> saved = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlements).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(2L);
        assertThat(saved.getValue().usedQuantity()).isEqualTo(1);
    }

    @Test
    void consumptionFailsWhenEveryAddonIsUsedUp() {
        UserEntitlement used = entitlement(1L, 95L, 1, false, 1, EntitlementSource.ADDON_PURCHASE);
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(used));

        assertThatThrownBy(() -> service.consume(MERCHANT, "SMART_REPORTING_ADDON", 1))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void usageIsDeniedOnceThePackageIsClosed() {
        Instant now = Instant.now();
        when(fulfillments.findActiveByMerchantId(MERCHANT)).thenReturn(Optional.of(new ActivePackage(
                1L, MERCHANT, 7L, 376L, 32L, "ULTIMATE_PACKAGE", null, null, PackageStatus.EXPIRED, now, now,
                SubscriptionKind.PAID, null)));

        assertThatThrownBy(() -> service.consume(MERCHANT, "SMART_REPORTING_ADDON", 1))
                .isInstanceOf(QuotaExceededException.class);
        assertThat(service.productAccess(MERCHANT, "SMART_REPORTING_ADDON").allowed()).isFalse();
    }

    @Test
    void usageStaysOpenDuringPaymentGrace() {
        Instant now = Instant.now();
        when(fulfillments.findActiveByMerchantId(MERCHANT)).thenReturn(Optional.of(new ActivePackage(
                1L, MERCHANT, 7L, 376L, 32L, "ULTIMATE_PACKAGE", null, null, PackageStatus.GRACE_PERIOD, now, now,
                SubscriptionKind.PAID, now.plusSeconds(86_400))));
        UserEntitlement addonRow = entitlement(144L, 95L, 10, false, 0, EntitlementSource.ADDON_PURCHASE);
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(addonRow));

        assertThat(service.consume(MERCHANT, "SMART_REPORTING_ADDON", 1).usedQuantity()).isEqualTo(1);
    }

    @Test
    void branchLimitAddsPackageAndAddonGrantsAndCountsWhatExists() {
        UserEntitlement packageRow = branch(1L, 1, EntitlementSource.PACKAGE_INCLUDE);
        UserEntitlement addonRow = branch(2L, 1, EntitlementSource.ADDON_PURCHASE);
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(packageRow, addonRow));
        when(counted.isCounted("QR_BRANCH")).thenReturn(true);
        when(counted.count(MERCHANT, "QR_BRANCH")).thenReturn(java.util.OptionalLong.of(1));

        assertThat(service.consume(MERCHANT, "QR_BRANCH", 1).usedQuantity()).isEqualTo(2);
        org.mockito.Mockito.verify(entitlements, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void branchLimitRefusesWithTheNumbersWhenEveryGrantIsTaken() {
        when(entitlements.lockActiveByMerchantId(MERCHANT)).thenReturn(List.of(branch(1L, 1, EntitlementSource.PACKAGE_INCLUDE)));
        when(counted.isCounted("QR_BRANCH")).thenReturn(true);
        when(counted.count(MERCHANT, "QR_BRANCH")).thenReturn(java.util.OptionalLong.of(1));

        assertThatThrownBy(() -> service.consume(MERCHANT, "QR_BRANCH", 1))
                .isInstanceOfSatisfying(QuotaExceededException.class, exception -> {
                    assertThat(exception.featureCode()).isEqualTo("QR_BRANCH");
                    assertThat(exception.limit()).isEqualTo(1L);
                    assertThat(exception.used()).isEqualTo(1L);
                });
    }

    @Test
    void deletingACountedResourceHasNothingToGiveBack() {
        when(counted.isCounted("QR_BRANCH")).thenReturn(true);

        assertThat(service.release(MERCHANT, "QR_BRANCH", 1)).isEmpty();
        org.mockito.Mockito.verify(entitlements, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void listedUsageFillsGrantsInOrderAndKeepsOverflowOnTheLast() {
        when(entitlements.findActiveByMerchantId(MERCHANT)).thenReturn(List.of(
                branch(1L, 1, EntitlementSource.PACKAGE_INCLUDE), branch(2L, 1, EntitlementSource.ADDON_PURCHASE)));
        when(counted.isCounted("QR_BRANCH")).thenReturn(true);
        when(counted.count(MERCHANT, "QR_BRANCH")).thenReturn(java.util.OptionalLong.of(3));

        List<Integer> used = service.listActive(MERCHANT).stream()
                .map(listed -> listed.entitlement().usedQuantity())
                .toList();

        assertThat(used).containsExactly(1, 2);
    }

    private static UserEntitlement branch(Long id, int quantity, EntitlementSource source) {
        Instant now = Instant.now();
        return new UserEntitlement(id, MERCHANT, 7L, null, 10L, ProductType.PACKAGE_PRODUCT,
                "QR_BRANCH", "QR_BRANCH_OWNER", quantity, false, 0, source, PackageStatus.ACTIVE, now, null, now, now);
    }

    private static UserEntitlement entitlement(
            Long id, Long productId, int quantity, boolean unlimited, int used, EntitlementSource source) {
        Instant now = Instant.now();
        return new UserEntitlement(id, MERCHANT, 7L, null, productId,
                source == EntitlementSource.ADDON_PURCHASE ? ProductType.ADDON_PRODUCT : ProductType.PACKAGE_PRODUCT,
                "SMART_REPORTING", "SMART_REPORTING_OWNER", quantity, unlimited, used, source,
                PackageStatus.ACTIVE, now, null, now, now);
    }
}
