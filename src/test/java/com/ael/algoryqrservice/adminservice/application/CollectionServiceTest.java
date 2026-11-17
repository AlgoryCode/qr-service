package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionEvent;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionEventRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 0);

    @Mock
    private CollectionCaseRepository caseRepository;
    @Mock
    private CollectionEventRepository eventRepository;
    @Mock
    private PurchaseRepository purchaseRepository;

    @InjectMocks
    private CollectionService service;

    private final List<CollectionEvent> events = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
        when(caseRepository.save(any())).thenAnswer(inv -> {
            CollectionCase c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(ids.incrementAndGet());
            }
            return c;
        });
        when(eventRepository.save(any())).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(caseRepository.findByClosedAtIsNull()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        AppTime.setClock(Clock.system(AppTime.ZONE));
    }

    @Test
    void reconcile_opensCaseForMerchantInArrears() {
        Purchase pastDue = purchase(5L, NOW.minusDays(2));
        pastDue.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        Purchase fine = purchase(6L, NOW.plusDays(20));
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of(pastDue, fine));

        assertThat(service.reconcile()).isEqualTo(1);

        ArgumentCaptor<CollectionCase> saved = ArgumentCaptor.forClass(CollectionCase.class);
        verify(caseRepository).save(saved.capture());
        CollectionCase opened = saved.getValue();
        assertThat(opened.getMerchantId()).isEqualTo(5L);
        assertThat(opened.getStatus()).isEqualTo(CollectionStatus.AWAITING_PAYMENT);
        assertThat(opened.getAmount()).isEqualByComparingTo("149.90");
        assertThat(opened.getDueSince()).isEqualTo(NOW.minusDays(2));
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getFromStatus()).isNull();
            assertThat(e.getActor()).isEqualTo(CollectionEvent.SYSTEM_ACTOR);
        });
    }

    @Test
    void reconcile_closesCaseAsPaidOncePackageRunsPastTheDebt() {
        CollectionCase open = openCase(5L, CollectionStatus.FORMAL_NOTICE, NOW.minusDays(40));
        when(caseRepository.findByClosedAtIsNull()).thenReturn(List.of(open));
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of(purchase(5L, NOW.plusDays(25))));

        assertThat(service.reconcile()).isEqualTo(1);

        assertThat(open.getStatus()).isEqualTo(CollectionStatus.PAID);
        assertThat(open.getClosedAt()).isEqualTo(NOW);
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getFromStatus()).isEqualTo(CollectionStatus.FORMAL_NOTICE);
            assertThat(e.getToStatus()).isEqualTo(CollectionStatus.PAID);
        });
    }

    @Test
    void reconcile_marksUntouchedCaseUnpaidAfterGraceButLeavesEscalatedOnes() {
        CollectionCase early = openCase(5L, CollectionStatus.REMINDED, NOW.minusDays(CollectionService.UNPAID_AFTER_DAYS + 1));
        CollectionCase legal = openCase(6L, CollectionStatus.LEGAL_FOLLOW_UP, NOW.minusDays(60));
        CollectionCase fresh = openCase(7L, CollectionStatus.AWAITING_PAYMENT, NOW.minusDays(3));
        when(caseRepository.findByClosedAtIsNull()).thenReturn(List.of(early, legal, fresh));
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of());

        assertThat(service.reconcile()).isEqualTo(1);

        assertThat(early.getStatus()).isEqualTo(CollectionStatus.UNPAID);
        assertThat(legal.getStatus()).isEqualTo(CollectionStatus.LEGAL_FOLLOW_UP);
        assertThat(fresh.getStatus()).isEqualTo(CollectionStatus.AWAITING_PAYMENT);
    }

    @Test
    void record_changesStatusClosesAndKeepsHistory() {
        CollectionCase open = openCase(5L, CollectionStatus.UNPAID, NOW.minusDays(30));
        when(caseRepository.findById(open.getId())).thenReturn(Optional.of(open));

        service.record(open.getId(), CollectionStatus.WRITTEN_OFF, "  Firma kapandı  ", "thamarat");

        assertThat(open.getStatus()).isEqualTo(CollectionStatus.WRITTEN_OFF);
        assertThat(open.getClosedAt()).isEqualTo(NOW);
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getNote()).isEqualTo("Firma kapandı");
            assertThat(e.getActor()).isEqualTo("thamarat");
        });
    }

    @Test
    void record_noteOnlyNeedsANote() {
        CollectionCase open = openCase(5L, CollectionStatus.UNPAID, NOW.minusDays(30));
        when(caseRepository.findById(open.getId())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.record(open.getId(), null, " ", "thamarat"))
                .isInstanceOf(BadRequestException.class);

        service.record(open.getId(), null, "Aradım, hafta sonu ödeyecek", "thamarat");
        assertThat(open.getStatus()).isEqualTo(CollectionStatus.UNPAID);
        assertThat(events).singleElement().satisfies(e -> assertThat(e.getToStatus()).isEqualTo(CollectionStatus.UNPAID));
        verify(caseRepository, never()).save(any());
    }

    @Test
    void reopen_isRefusedWhileAnotherCaseIsOpen() {
        CollectionCase closed = openCase(5L, CollectionStatus.PAID, NOW.minusDays(30));
        closed.setClosedAt(NOW.minusDays(1));
        CollectionCase other = openCase(5L, CollectionStatus.AWAITING_PAYMENT, NOW.minusDays(2));
        when(caseRepository.findById(closed.getId())).thenReturn(Optional.of(closed));
        when(caseRepository.findFirstByMerchantIdAndClosedAtIsNull(5L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.record(closed.getId(), CollectionStatus.UNPAID, null, "thamarat"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void open_byHandUsesLatestPaidPackageAndRefusesDuplicates() {
        when(caseRepository.findFirstByMerchantIdAndClosedAtIsNull(5L)).thenReturn(Optional.empty());
        when(purchaseRepository.findByMerchantIdOrderByPurchasedAtDesc(5L)).thenReturn(List.of(purchase(5L, NOW.minusDays(10))));

        CollectionCase opened = service.open(5L, "Son dönem ödenmedi", "thamarat");
        assertThat(opened.getStatus()).isEqualTo(CollectionStatus.AWAITING_PAYMENT);
        assertThat(opened.getDueSince()).isEqualTo(NOW.minusDays(10));

        when(caseRepository.findFirstByMerchantIdAndClosedAtIsNull(5L)).thenReturn(Optional.of(opened));
        assertThatThrownBy(() -> service.open(5L, null, "thamarat")).isInstanceOf(ConflictException.class);
    }

    private CollectionCase openCase(Long merchantId, CollectionStatus status, LocalDateTime dueSince) {
        return CollectionCase.builder()
                .id(ids.incrementAndGet())
                .merchantId(merchantId)
                .status(status)
                .dueSince(dueSince)
                .openedAt(dueSince)
                .statusAt(dueSince)
                .build();
    }

    private static Purchase purchase(Long merchantId, LocalDateTime expiresAt) {
        Purchase purchase = new Purchase();
        purchase.setId(merchantId * 10);
        purchase.setMerchantId(merchantId);
        purchase.setPackageId(1L);
        purchase.setPurchaseType(PurchaseType.PAID);
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId("sub-" + merchantId);
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setPrice(new BigDecimal("149.90"));
        purchase.setCurrency("TRY");
        purchase.setExpiresAt(expiresAt);
        return purchase;
    }
}
