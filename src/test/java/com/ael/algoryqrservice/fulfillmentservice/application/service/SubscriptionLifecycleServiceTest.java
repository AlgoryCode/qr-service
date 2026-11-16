package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.config.BillingLifecycleProperties;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.SubscriptionHistoryPort;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionLifecycleServiceTest {

    private static final long MERCHANT = 7L;

    private FulfillmentRepositoryPort repository;
    private FulfillmentService fulfillmentService;
    private PackageCatalogPort catalog;
    private SubscriptionEvents events;
    private SubscriptionLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = mock(FulfillmentRepositoryPort.class);
        fulfillmentService = mock(FulfillmentService.class);
        catalog = mock(PackageCatalogPort.class);
        events = mock(SubscriptionEvents.class);
        when(events.emit(any())).thenReturn(true);
        service = new SubscriptionLifecycleService(repository, fulfillmentService, catalog, events,
                new BillingLifecycleProperties(14, 7, List.of(7, 2, 0)), mock(SubscriptionHistoryPort.class));
        PlanPackage trial = plan(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
        PlanPackage free = plan(CatalogPackages.FREE_PACKAGE);
        when(catalog.findByCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)).thenReturn(Optional.of(trial));
        when(catalog.findByCode(CatalogPackages.FREE_PACKAGE)).thenReturn(Optional.of(free));
    }

    @Test
    void aNewMerchantGetsTheFullTrialOnce() {
        assertThat(service.startTrial(MERCHANT)).isTrue();

        ArgumentCaptor<FulfillmentCommand> command = ArgumentCaptor.forClass(FulfillmentCommand.class);
        verify(fulfillmentService).fulfillPaidPackage(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo("trial:" + MERCHANT);
        assertThat(emitted().type()).isEqualTo(SubscriptionEventType.TRIAL_STARTED);
    }

    @Test
    void aMerchantThatEverHadAPackageCannotRestartTheTrial() {
        when(repository.hasAnyFulfillment(MERCHANT)).thenReturn(true);

        assertThat(service.startTrial(MERCHANT)).isFalse();

        verify(fulfillmentService, never()).fulfillPaidPackage(any());
    }

    @Test
    void aFailedRenewalKeepsThePaidPackageOpenUntilGraceEnds() {
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 42L)));
        Instant graceEnds = Instant.parse("2026-11-23T09:00:00Z");

        service.enterGrace(MERCHANT, 42L, graceEnds, "payment_failed");

        ArgumentCaptor<ActivePackage> saved = ArgumentCaptor.forClass(ActivePackage.class);
        verify(repository).saveActivePackage(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(PackageStatus.GRACE_PERIOD);
        assertThat(saved.getValue().graceEndsAt()).isEqualTo(graceEnds);
        assertThat(saved.getValue().grantsAccess()).isTrue();
        assertThat(emitted().type()).isEqualTo(SubscriptionEventType.PAYMENT_FAILED);
    }

    @Test
    void graceIgnoresTrialsAndOldPurchases() {
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.TRIAL, PackageStatus.ACTIVE, 42L)));
        service.enterGrace(MERCHANT, 42L, null, "payment_failed");

        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 42L)));
        service.enterGrace(MERCHANT, 41L, null, "payment_failed");

        verify(repository, never()).saveActivePackage(any());
    }

    @Test
    void anEndedTrialFallsBackToTheFreePackage() {
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.TRIAL, PackageStatus.ACTIVE, 0L)));

        assertThat(service.downgradeToFree(MERCHANT, "trial_ended")).isTrue();

        ArgumentCaptor<FulfillmentCommand> command = ArgumentCaptor.forClass(FulfillmentCommand.class);
        verify(fulfillmentService).fulfillPaidPackage(command.capture());
        assertThat(command.getValue().packageCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);
        assertThat(emitted().type()).isEqualTo(SubscriptionEventType.TRIAL_ENDED);
    }

    @Test
    void anUnpaidPackageFallsBackToFreeAndSaysSo() {
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.PAID, PackageStatus.GRACE_PERIOD, 42L)));

        assertThat(service.downgradeToFree(MERCHANT, "grace_period_expired")).isTrue();

        SubscriptionEvent event = emitted();
        assertThat(event.type()).isEqualTo(SubscriptionEventType.DOWNGRADED_TO_FREE);
        assertThat(event.previousPackageCode()).isEqualTo("PRO_PACKAGE");
    }

    @Test
    void merchantsAlreadyOnFreeAreLeftAlone() {
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.FREE, PackageStatus.ACTIVE, 0L)));

        assertThat(service.downgradeToFree(MERCHANT, "again")).isFalse();

        verify(fulfillmentService, never()).fulfillPaidPackage(any());
    }

    @Test
    void withoutAFreePackageTheAccountClosesInstead() {
        when(catalog.findByCode(CatalogPackages.FREE_PACKAGE)).thenReturn(Optional.empty());
        when(repository.findActiveByMerchantId(MERCHANT))
                .thenReturn(Optional.of(active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 42L)));

        assertThat(service.downgradeToFree(MERCHANT, "period_ended")).isFalse();

        ArgumentCaptor<ActivePackage> saved = ArgumentCaptor.forClass(ActivePackage.class);
        verify(repository).saveActivePackage(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(PackageStatus.EXPIRED);
    }

    @Test
    void dueReasonNamesWhyThePackageEnds() {
        assertThat(SubscriptionLifecycleService.dueReason(active(SubscriptionKind.TRIAL, PackageStatus.ACTIVE, 0L)))
                .isEqualTo("trial_ended");
        assertThat(SubscriptionLifecycleService.dueReason(active(SubscriptionKind.PAID, PackageStatus.GRACE_PERIOD, 1L)))
                .isEqualTo("grace_period_expired");
        assertThat(SubscriptionLifecycleService.dueReason(active(SubscriptionKind.PAID, PackageStatus.ACTIVE, 1L)))
                .isEqualTo("period_ended_without_renewal");
    }

    private SubscriptionEvent emitted() {
        ArgumentCaptor<SubscriptionEvent> event = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(events).emit(event.capture());
        return event.getValue();
    }

    private static ActivePackage active(SubscriptionKind kind, PackageStatus status, Long purchaseId) {
        String code = switch (kind) {
            case TRIAL -> CatalogPackages.ULTIMATE_TRIAL_PACKAGE;
            case FREE -> CatalogPackages.FREE_PACKAGE;
            default -> "PRO_PACKAGE";
        };
        return new ActivePackage(1L, MERCHANT, 10L, purchaseId, 5L, code, LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1), status, Instant.now(), Instant.now(), kind,
                status == PackageStatus.GRACE_PERIOD ? Instant.now().minusSeconds(60) : null);
    }

    private static PlanPackage plan(String code) {
        PlanPackage plan = mock(PlanPackage.class);
        when(plan.code()).thenReturn(code);
        when(plan.active()).thenReturn(true);
        when(plan.id()).thenReturn((long) code.hashCode());
        return plan;
    }
}
