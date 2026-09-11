package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackagePeriodExtenderTest {

    @Mock
    PurchaseRepository purchaseRepository;
    @Mock
    FulfillmentGrantService fulfillmentGrantService;
    @Mock
    PackageEntitlementWriter entitlementWriter;
    @Mock
    MenuPublicAccessService menuPublicAccessService;
    @Mock
    PurchaseLogService purchaseLogService;

    PackagePeriodExtender extender;

    @BeforeEach
    void setUp() {
        extender = new PackagePeriodExtender(
                purchaseRepository,
                fulfillmentGrantService,
                entitlementWriter,
                menuPublicAccessService,
                purchaseLogService
        );
    }

    @Test
    void extend_whenFutureExpiry_thenAddDaysFromExpiry() {
        LocalDateTime expires = LocalDateTime.now().plusDays(10);
        Purchase purchase = host(expires, PaymentStyle.ONE_TIME);
        when(purchaseRepository.findByUserIdAndStatusAndPurchaseType(7L, PurchaseStatus.ACTIVE, PurchaseType.ADD_ON))
                .thenReturn(List.of());

        Purchase result = extender.extend(purchase, 5);

        assertThat(result.getExpiresAt()).isEqualTo(expires.plusDays(5));
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(result.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(fulfillmentGrantService).extendPurchasePeriod(purchase);
        verify(entitlementWriter).synchronizePeriod(purchase);
        verify(menuPublicAccessService).syncForUser(7L);
        verify(purchaseLogService).log(eq(10L), eq(7L), eq(PurchaseLogAction.PURCHASE_EXTENDED), anyString());
    }

    @Test
    void extend_whenExpired_thenAddDaysFromNowAndSyncAddon() {
        Purchase host = host(LocalDateTime.now().minusDays(2), PaymentStyle.SUBSCRIPTION);
        Purchase addon = Purchase.builder()
                .id(22L)
                .userId(7L)
                .packageId(3L)
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusDays(2))
                .build();
        when(purchaseRepository.findByUserIdAndStatusAndPurchaseType(7L, PurchaseStatus.ACTIVE, PurchaseType.ADD_ON))
                .thenReturn(List.of(addon));

        Purchase result = extender.extend(host, 30);

        assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(addon.getExpiresAt()).isEqualTo(result.getExpiresAt());
        verify(fulfillmentGrantService).extendPurchasePeriod(host);
        verify(fulfillmentGrantService).extendPurchasePeriod(addon);
        verify(entitlementWriter).synchronizePeriod(addon);
    }

    @Test
    void extend_whenAddon_thenReject() {
        Purchase addon = Purchase.builder()
                .id(22L)
                .purchaseType(PurchaseType.ADD_ON)
                .build();

        assertThatThrownBy(() -> extender.extend(addon, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Eklenti");
    }

    private static Purchase host(LocalDateTime expiresAt, PaymentStyle style) {
        return Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageId(3L)
                .packageName("Pro")
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(style)
                .status(PurchaseStatus.EXPIRED)
                .expiresAt(expiresAt)
                .build();
    }
}
