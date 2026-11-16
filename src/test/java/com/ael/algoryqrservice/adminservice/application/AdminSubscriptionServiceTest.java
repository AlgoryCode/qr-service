package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminPackageDtos;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionEvents;
import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.purchase.lifecycle.RemoteSubscriptionCanceller;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSubscriptionServiceTest {

    private static final long MERCHANT = 7L;

    private FulfillmentService fulfillmentService;
    private SubscriptionLifecycleService lifecycle;
    private PackageCatalogPort catalog;
    private SubscriptionEvents events;
    private PurchaseRepository purchases;
    private RemoteSubscriptionCanceller canceller;
    private AdminSubscriptionService service;

    @BeforeEach
    void setUp() {
        fulfillmentService = mock(FulfillmentService.class);
        lifecycle = mock(SubscriptionLifecycleService.class);
        catalog = mock(PackageCatalogPort.class);
        events = mock(SubscriptionEvents.class);
        purchases = mock(PurchaseRepository.class);
        canceller = mock(RemoteSubscriptionCanceller.class);
        service = new AdminSubscriptionService(fulfillmentService, lifecycle, catalog, events, purchases, canceller);
        when(fulfillmentService.listFulfillments(any(), any())).thenReturn(Page.empty());
        when(catalog.findPurchasable(any())).thenReturn(Page.empty());
        PlanPackage trial = plan(1L, CatalogPackages.ULTIMATE_TRIAL_PACKAGE, "Deneme", 0);
        when(catalog.findById(1L)).thenReturn(Optional.of(trial));
        when(catalog.findByCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)).thenReturn(Optional.of(trial));
    }

    @Test
    void extendingATrialRefulfillsItWithTheLaterEndDate() {
        LocalDate start = today().minusDays(10);
        LocalDate end = today().plusDays(4);
        current(active(SubscriptionKind.TRIAL, PackageStatus.ACTIVE, 1L, CatalogPackages.ULTIMATE_TRIAL_PACKAGE, start, end));

        AdminPackageDtos.ExtendTrialResponse response = service.extendTrial(MERCHANT, 5);

        FulfillmentCommand command = fulfilled();
        assertThat(command.eventId()).startsWith("admin-trial-extend:" + MERCHANT + ":");
        assertThat(command.packageCode()).isEqualTo(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
        assertThat(command.periodStart()).isEqualTo(start);
        assertThat(command.periodEnd()).isEqualTo(end.plusDays(5));
        assertThat(command.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getDaysAdded()).isEqualTo(5);
        assertThat(response.getPackageName()).isEqualTo("Deneme");
        assertThat(response.getExpiresAt()).isEqualTo(end.plusDays(5).atStartOfDay());
        assertThat(response.getPurchaseId()).isNull();
    }

    @Test
    void onlyATrialCanBeExtendedOrEnded() {
        current(active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 2L, "PRO_PACKAGE", today(), today().plusDays(30)));

        assertThatThrownBy(() -> service.extendTrial(MERCHANT, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.endTrial(MERCHANT)).isInstanceOf(BadRequestException.class);
        verify(fulfillmentService, never()).fulfillPaidPackage(any());
        verify(lifecycle, never()).downgradeToFree(any(), anyString());
    }

    @Test
    void endingATrialDowngradesToFree() {
        current(active(SubscriptionKind.TRIAL, PackageStatus.ACTIVE, 1L, CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                today(), today().plusDays(3)));

        AdminPackageDtos.EndTrialResponse response = service.endTrial(MERCHANT);

        verify(lifecycle).downgradeToFree(MERCHANT, "admin_ended_trial");
        assertThat(response.getPackageName()).isEqualTo("Deneme");
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void deactivatingAPackageDowngradesToFree() {
        when(catalog.findById(2L)).thenReturn(Optional.of(plan(2L, "PRO_PACKAGE", "Pro", 20)));
        ActivePackage paid = active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 2L, "PRO_PACKAGE",
                today(), today().plusDays(30));
        current(new ActivePackage(paid.id(), paid.merchantId(), paid.fulfillmentId(), 55L, paid.packageId(),
                paid.packageCode(), paid.periodStart(), paid.periodEnd(), paid.status(), paid.activatedAt(),
                paid.updatedAt(), paid.kind(), null));

        Purchase purchase = Purchase.builder().id(55L).merchantId(MERCHANT).build();
        when(purchases.findById(55L)).thenReturn(Optional.of(purchase));

        AdminPackageDtos.PackageLifecycleResponse response = service.deactivatePackage(MERCHANT);

        verify(lifecycle).downgradeToFree(MERCHANT, "admin_deactivated");
        verify(canceller).cancelIfNeeded(purchase);
        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        assertThat(response.getPurchaseId()).isEqualTo(55L);
        assertThat(response.getPackageName()).isEqualTo("Pro");
    }

    @Test
    void aFreeOrClosedPackageCannotBeDeactivated() {
        current(active(SubscriptionKind.FREE, PackageStatus.ACTIVE, 3L, CatalogPackages.FREE_PACKAGE, today(), null));
        assertThatThrownBy(() -> service.deactivatePackage(MERCHANT)).isInstanceOf(BadRequestException.class);

        current(active(SubscriptionKind.PAID, PackageStatus.EXPIRED, 2L, "PRO_PACKAGE", today(), today()));
        assertThatThrownBy(() -> service.deactivatePackage(MERCHANT)).isInstanceOf(BadRequestException.class);

        verify(lifecycle, never()).downgradeToFree(any(), anyString());
    }

    @Test
    void grantingReusesTheLastPaidPackageAsAGrantAndRecordsTheEvent() {
        PlanPackage pro = plan(2L, "PRO_PACKAGE", "Pro", 20);
        when(catalog.findById(2L)).thenReturn(Optional.of(pro));
        when(fulfillmentService.listFulfillments(any(), any())).thenReturn(new PageImpl<>(List.of(
                fulfillment(1L, CatalogPackages.ULTIMATE_TRIAL_PACKAGE, 0L, BigDecimal.ZERO),
                fulfillment(2L, "PRO_PACKAGE", 55L, new BigDecimal("499")))));
        current(active(SubscriptionKind.FREE, PackageStatus.ACTIVE, 3L, CatalogPackages.FREE_PACKAGE, today(), null));

        AdminPackageDtos.PackageLifecycleResponse response = service.grantPackage(MERCHANT, 30);

        FulfillmentCommand command = fulfilled();
        assertThat(command.eventId()).startsWith("admin-grant:" + MERCHANT + ":");
        assertThat(command.packageCode()).isEqualTo("PRO_PACKAGE");
        assertThat(command.periodStart()).isEqualTo(today());
        assertThat(command.periodEnd()).isEqualTo(today().plusDays(30));
        // A zero amount makes the fulfillment a GRANT, which the lifecycle job ends into FREE.
        assertThat(command.amount()).isEqualByComparingTo(BigDecimal.ZERO);

        ArgumentCaptor<SubscriptionEvent> event = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(events).emit(event.capture());
        assertThat(event.getValue().type()).isEqualTo(SubscriptionEventType.PACKAGE_ACTIVATED);
        assertThat(event.getValue().actor()).isEqualTo("admin");
        assertThat(event.getValue().previousPackageCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getPackageName()).isEqualTo("Pro");
        assertThat(response.getDaysAdded()).isEqualTo(30);
        assertThat(response.getExpiresAt()).isEqualTo(today().plusDays(30).atStartOfDay());
        assertThat(response.getPurchaseId()).isNull();
    }

    @Test
    void aMerchantThatNeverPaidGetsTheHighestPriorityPurchasablePackage() {
        when(catalog.findPurchasable(any())).thenReturn(new PageImpl<>(List.of(
                plan(4L, "STARTER_PACKAGE", "Starter", 10),
                plan(5L, "ULTIMATE_PACKAGE", "Ultimate", 30),
                plan(6L, "PRO_PACKAGE", "Pro", 20))));

        service.grantPackage(MERCHANT, 7);

        assertThat(fulfilled().packageCode()).isEqualTo("ULTIMATE_PACKAGE");
    }

    @Test
    void grantDaysAreBounded() {
        assertThatThrownBy(() -> service.grantPackage(MERCHANT, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.extendTrial(MERCHANT, 366)).isInstanceOf(BadRequestException.class);
    }

    private void current(ActivePackage active) {
        when(fulfillmentService.findCurrentPackage(MERCHANT)).thenReturn(Optional.of(active));
    }

    private FulfillmentCommand fulfilled() {
        ArgumentCaptor<FulfillmentCommand> command = ArgumentCaptor.forClass(FulfillmentCommand.class);
        verify(fulfillmentService).fulfillPaidPackage(command.capture());
        return command.getValue();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static ActivePackage active(SubscriptionKind kind, PackageStatus status, Long packageId, String code,
                                        LocalDate start, LocalDate end) {
        return new ActivePackage(1L, MERCHANT, 100L, 0L, packageId, code, start, end, status,
                Instant.now(), Instant.now(), kind, null);
    }

    private static PackageFulfillment fulfillment(Long packageId, String code, Long purchaseId, BigDecimal amount) {
        return new PackageFulfillment(null, "e-" + code, "t", purchaseId, MERCHANT, packageId, code,
                null, null, null, null, today(), today().plusDays(30), amount, "TRY",
                FulfillmentStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private static PlanPackage plan(Long id, String code, String name, int priority) {
        return new PlanPackage(id, code, name, null, PackageKind.CATALOG, List.of(), BigDecimal.TEN, null,
                null, null, "TRY", true, 30, priority, true, false, List.of(), Instant.now(), Instant.now());
    }
}
