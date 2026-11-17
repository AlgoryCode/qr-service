package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.billing.dto.AddonCartLineRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.AddonCartPurchaseRequest;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.PurchaseItem;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseItemRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackagePurchaseGuardTest {

    private static final long MERCHANT = 7L;

    @Mock private PurchaseRepository purchases;
    @Mock private com.ael.algoryqrservice.qrservice.purchase.lifecycle.RemoteSubscriptionCanceller canceller;
    @Mock private com.ael.algoryqrservice.qrservice.coupon.service.CouponRedemptionService coupons;
    @Mock private PurchaseLogService purchaseLog;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks private PurchaseService service;

    @Test
    void activePaidPackageBlocksANewPackageCheckout() {
        when(purchases.findByMerchantIdAndStatusAndPurchaseType(MERCHANT, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of(paid(AppTime.nowLocal().plusDays(10))));

        assertThatThrownBy(() -> service.rejectIfPaidPackageActive(MERCHANT))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("paket değiştirmeyi");
    }

    @Test
    void lapsedPaidPackageDoesNotBlock() {
        when(purchases.findByMerchantIdAndStatusAndPurchaseType(MERCHANT, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of(paid(AppTime.nowLocal().minusHours(1))));

        assertThatCode(() -> service.rejectIfPaidPackageActive(MERCHANT)).doesNotThrowAnyException();
    }

    @Test
    void latePaymentForASecondPackageIsRefundedNotActivated() {
        Purchase current = paid(AppTime.nowLocal().plusDays(10));
        Purchase late = paid(null);
        late.setId(9L);
        late.setStatus(PurchaseStatus.CANCELLED);
        late.setPrice(new java.math.BigDecimal("499.00"));
        when(purchases.findByMerchantIdAndStatusAndPurchaseType(MERCHANT, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of(current));
        com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto event =
                new com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto();
        event.setConversationId("conv-9");
        event.setSubscriptionId("sub-9");

        assertThat(service.refundIfSecondPackage(late, event)).isTrue();

        assertThat(late.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(late.getCancellationReason())
                .isEqualTo(com.ael.algoryqrservice.qrservice.model.enums.PurchaseCancellationReason.DUPLICATE_PACKAGE);
        assertThat(late.getRefundStatus()).isEqualTo(com.ael.algoryqrservice.qrservice.model.enums.RefundStatus.PENDING);
        assertThat(late.getSubscriptionId()).isEqualTo("sub-9");
        verify(events).publishEvent(new DuplicatePackageRefunder.Requested(9L));
        verify(coupons).release(late);
    }

    @Test
    void lapsedPackageIsReplacedAndItsSubscriptionStopped() {
        Purchase lapsed = paid(AppTime.nowLocal().minusDays(2));
        Purchase fresh = paid(null);
        fresh.setId(9L);
        fresh.setStatus(PurchaseStatus.PENDING);
        when(purchases.findByMerchantIdAndStatusAndPurchaseType(MERCHANT, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of(lapsed));

        assertThat(service.refundIfSecondPackage(fresh,
                new com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto())).isFalse();

        assertThat(lapsed.getStatus()).isEqualTo(PurchaseStatus.SUPERSEDED);
        verify(canceller).cancelIfNeeded(lapsed);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void renewalOfTheActivePackageIsLeftAlone() {
        Purchase current = paid(AppTime.nowLocal().plusDays(10));

        assertThat(service.refundIfSecondPackage(current,
                new com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto())).isFalse();

        verify(purchases, never()).findByMerchantIdAndStatusAndPurchaseType(any(), any(), any());
    }

    private static Purchase paid(LocalDateTime expiresAt) {
        Purchase purchase = new Purchase();
        purchase.setId(1L);
        purchase.setMerchantId(MERCHANT);
        purchase.setPackageId(3L);
        purchase.setPurchaseType(PurchaseType.PAID);
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentStyle(PaymentStyle.ONE_TIME);
        purchase.setStartsAt(AppTime.nowLocal().minusDays(5));
        purchase.setExpiresAt(expiresAt);
        return purchase;
    }

    private static Product module(Long id, String code, boolean consumable) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(code);
        product.setFeatureCode(code);
        product.setUnitPrice(new BigDecimal("50"));
        product.setVatRate(new BigDecimal("20"));
        product.setBillingType(consumable ? ProductBillingType.ONE_TIME : ProductBillingType.RECURRING);
        product.setConsumable(consumable);
        product.setAddonPurchasable(true);
        product.setActive(true);
        return product;
    }

    private static User user() {
        User user = new User();
        user.setId(MERCHANT);
        return user;
    }

    private static AddonCartPurchaseRequest cart(String code) {
        AddonCartPurchaseRequest request = new AddonCartPurchaseRequest();
        request.setModules(List.of(new AddonCartLineRequest(code, 1)));
        return request;
    }
}
