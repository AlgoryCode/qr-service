package com.ael.algoryqrservice.qrservice.billing;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.qrservice.billing.service.entitlement.AddonEntitlementService;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.PurchaseItem;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddonEntitlementServiceTest {

    private final FulfillmentService fulfillmentService = mock(FulfillmentService.class);
    private final PurchaseItemRepository items = mock(PurchaseItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AddonEntitlementService service = new AddonEntitlementService(fulfillmentService, items, products);

    @Test
    void grantsOneLinePerPurchasedProductWithFeatureAndQuantity() {
        Purchase purchase = Purchase.builder().id(900L).merchantId(1L).purchaseType(PurchaseType.ADD_ON)
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(items.findByPurchaseId(900L)).thenReturn(List.of(
                PurchaseItem.builder().purchaseId(900L).productId(95L).quantity(3).build(),
                PurchaseItem.builder().purchaseId(900L).productId(40L).quantity(1).build()));
        when(products.findAllById(any())).thenReturn(List.of(
                Product.builder().id(95L).code("SMART_REPORTING_ADDON").featureCode("SMART_REPORTING")
                        .scopeCode("SMART_REPORTING_OWNER").consumable(true).billingType(ProductBillingType.ONE_TIME).build(),
                Product.builder().id(40L).code("QR_BRANCH").featureCode("QR_BRANCH")
                        .consumable(false).billingType(ProductBillingType.RECURRING).build()));

        service.grant(purchase);

        ArgumentCaptor<AddonGrant> grant = ArgumentCaptor.forClass(AddonGrant.class);
        verify(fulfillmentService).grantAddon(grant.capture());
        assertThat(grant.getValue().purchaseId()).isEqualTo(900L);
        AddonGrant.Line reports = grant.getValue().lines().get(0);
        assertThat(reports.featureCode()).isEqualTo("SMART_REPORTING");
        assertThat(reports.quantity()).isEqualTo(3);
        assertThat(reports.expiresAt()).as("one-time credits do not expire").isNull();
        assertThat(grant.getValue().lines().get(1).expiresAt()).as("recurring add-ons follow the purchase").isNotNull();
    }

    @Test
    void ignoresPackagePurchases() {
        service.grant(Purchase.builder().id(1L).merchantId(1L).purchaseType(PurchaseType.PAID).build());
        service.revoke(Purchase.builder().id(1L).merchantId(1L).purchaseType(PurchaseType.PAID).build());

        verify(fulfillmentService, never()).grantAddon(any());
        verify(fulfillmentService, never()).revokeAddon(any());
    }
}
