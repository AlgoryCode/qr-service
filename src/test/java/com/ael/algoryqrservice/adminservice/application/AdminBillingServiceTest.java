package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.AccountPage;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.AccountRow;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBillingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 0);

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository collectionCaseRepository;

    @InjectMocks
    private AdminBillingService service;

    @BeforeEach
    void freezeClock() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
    }

    @AfterEach
    void releaseClock() {
        AppTime.setClock(Clock.system(AppTime.ZONE));
    }

    @Test
    void standing_classifiesEachCase() {
        assertThat(AdminBillingService.standing(subscription(1L, NOW.plusDays(20)), NOW, 7)).isEqualTo(Standing.PAID);
        assertThat(AdminBillingService.standing(subscription(1L, NOW.plusDays(3)), NOW, 7)).isEqualTo(Standing.DUE_SOON);

        Purchase pastDue = subscription(1L, NOW.plusDays(20));
        pastDue.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        assertThat(AdminBillingService.standing(pastDue, NOW, 7)).isEqualTo(Standing.OVERDUE);

        Purchase grace = subscription(1L, NOW.plusDays(2));
        grace.setSubscriptionStatus(SubscriptionStatus.GRACE_PERIOD);
        assertThat(AdminBillingService.standing(grace, NOW, 7)).isEqualTo(Standing.OVERDUE);

        // Renewing subscription still active after its end: the renewal never came.
        assertThat(AdminBillingService.standing(subscription(1L, NOW.minusDays(1)), NOW, 7)).isEqualTo(Standing.OVERDUE);

        // One-time package past its end owes nothing; it just ran out.
        Purchase oneTime = subscription(1L, NOW.minusDays(1));
        oneTime.setPaymentStyle(PaymentStyle.ONE_TIME);
        oneTime.setSubscriptionId(null);
        assertThat(AdminBillingService.standing(oneTime, NOW, 7)).isEqualTo(Standing.LAPSED);

        Purchase expired = subscription(1L, NOW.minusDays(5));
        expired.setStatus(PurchaseStatus.EXPIRED);
        assertThat(AdminBillingService.standing(expired, NOW, 7)).isEqualTo(Standing.LAPSED);
    }

    @Test
    void currentPerMerchant_prefersActiveOverLapsed() {
        Purchase lapsed = subscription(7L, NOW.minusDays(3));
        lapsed.setId(70L);
        lapsed.setStatus(PurchaseStatus.EXPIRED);
        Purchase active = subscription(7L, NOW.plusDays(25));
        active.setId(71L);

        assertThat(AdminBillingService.currentPerMerchant(List.of(lapsed, active)))
                .extracting(Purchase::getId).containsExactly(71L);
    }

    @Test
    void list_filtersByStandingAndCountsEveryStanding() {
        Purchase paid = subscription(1L, NOW.plusDays(20));
        Purchase dueSoon = subscription(2L, NOW.plusDays(2));
        Purchase overdue = subscription(3L, NOW.plusDays(10));
        overdue.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of(paid, dueSoon, overdue));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user(1L, "Ayşe", "Kaya"), user(2L, "Ali", "Demir"), user(3L, "Can", "Er")));

        AccountPage page = service.list("OVERDUE", null, null, null, null, 0, 20);

        assertThat(page.content()).extracting(AccountRow::merchantId).containsExactly(3L);
        AccountRow row = page.content().get(0);
        assertThat(row.merchantName()).isEqualTo("Can Er");
        assertThat(row.outstandingAmount()).isEqualByComparingTo("149.90");
        assertThat(page.summary().paid()).isEqualTo(1);
        assertThat(page.summary().dueSoon()).isEqualTo(1);
        assertThat(page.summary().overdue()).isEqualTo(1);
        assertThat(page.summary().overdueAmount()).isEqualByComparingTo("149.90");
        assertThat(page.summary().dueSoonAmount()).isEqualByComparingTo("149.90");
    }

    @Test
    void list_searchesByNameAndSortsByDueDate() {
        Purchase later = subscription(1L, NOW.plusDays(20));
        Purchase sooner = subscription(2L, NOW.plusDays(4));
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of(later, sooner));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(user(1L, "Ayşe", "Kaya"), user(2L, "Ali", "Kaya")));

        AccountPage all = service.list(null, "kaya", null, 30, null, 0, 20);
        assertThat(all.content()).extracting(AccountRow::merchantId).containsExactly(2L, 1L);
        assertThat(all.content().get(0).daysUntilDue()).isEqualTo(4);

        AccountPage onlyAyse = service.list(null, "ayşe", null, null, null, 0, 20);
        assertThat(onlyAyse.content()).extracting(AccountRow::merchantId).containsExactly(1L);
    }

    @Test
    void list_showsAndFiltersByCollectionCase() {
        Purchase overdue = subscription(3L, NOW.plusDays(10));
        overdue.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        Purchase paid = subscription(1L, NOW.plusDays(20));
        when(purchaseRepository.findBillingStandingCandidates(any())).thenReturn(List.of(paid, overdue));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
        when(collectionCaseRepository.findByClosedAtIsNull()).thenReturn(List.of(
                com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase.builder()
                        .id(9L).merchantId(3L).purchaseId(30L)
                        .status(com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus.FORMAL_NOTICE)
                        .statusAt(NOW.minusDays(1)).openedAt(NOW.minusDays(20)).build()));

        AccountPage open = service.list(null, "OPEN", null, null, null, null, 0, 20);
        assertThat(open.content()).extracting(AccountRow::merchantId).containsExactly(3L);
        assertThat(open.content().get(0).collectionStatus().name()).isEqualTo("FORMAL_NOTICE");
        assertThat(open.summary().openCollections()).isEqualTo(1);

        AccountPage none = service.list(null, "NONE", null, null, null, null, 0, 20);
        assertThat(none.content()).extracting(AccountRow::merchantId).containsExactly(1L);

        assertThatThrownBy(() -> service.list(null, "SUED", null, null, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void list_rejectsUnknownStandingAndWindow() {
        assertThatThrownBy(() -> service.list("LATE", null, null, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(null, null, null, 0, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void daysBetween_roundsTowardTheDueDate() {
        assertThat(AdminBillingService.daysBetween(NOW, NOW.plusHours(1))).isEqualTo(1);
        assertThat(AdminBillingService.daysBetween(NOW, NOW.minusHours(1))).isEqualTo(-1);
        assertThat(AdminBillingService.daysBetween(NOW, NOW.plusDays(3))).isEqualTo(3);
    }

    private static Purchase subscription(Long merchantId, LocalDateTime expiresAt) {
        Purchase purchase = new Purchase();
        purchase.setId(merchantId * 10);
        purchase.setMerchantId(merchantId);
        purchase.setPackageId(1L);
        purchase.setPackageCode("PRO");
        purchase.setPackageName("Pro");
        purchase.setPurchaseType(PurchaseType.PAID);
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        purchase.setSubscriptionId("sub-" + merchantId);
        purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        purchase.setPrice(new BigDecimal("149.90"));
        purchase.setCurrency("TRY");
        purchase.setPurchasedAt(expiresAt.minusMonths(1));
        purchase.setExpiresAt(expiresAt);
        return purchase;
    }

    private static User user(Long id, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(first.toLowerCase() + "@example.com");
        return user;
    }
}
