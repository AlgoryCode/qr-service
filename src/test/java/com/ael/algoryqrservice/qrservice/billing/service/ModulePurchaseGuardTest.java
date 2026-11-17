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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModulePurchaseGuardTest {

    private static final long MERCHANT = 7L;

    @Mock private ProductRepository products;
    @Mock private PurchaseRepository purchases;
    @Mock private PurchaseItemRepository purchaseItems;
    @Mock private PlanPackageRepository packages;
    @Mock private com.ael.algoryqrservice.qrservice.billing.service.entitlement.PurchaseExpiryService expiry;
    @Spy private PackagePricingService pricing = new PackagePricingService();
    @InjectMocks private AddonPurchaseService service;

    private final Product waiter = module(20L, "WAITER_PANEL", false);
    private final Product credits = module(21L, "SMART_REPORTING_ADDON", true);

    private void catalog(Purchase host, List<Purchase> active, List<PurchaseItem> lines, PlanPackage pkg) {
        Map<Long, Product> byId = Map.of(waiter.getId(), waiter, credits.getId(), credits);
        when(products.findByCode(any())).thenAnswer(inv -> byId.values().stream()
                .filter(p -> p.getCode().equals(inv.getArgument(0))).findFirst());
        when(products.findAllById(anyCollection())).thenAnswer(inv -> inv.<Collection<Long>>getArgument(0).stream()
                .map(byId::get).filter(p -> p != null).toList());
        when(purchases.findByMerchantIdAndStatus(MERCHANT, PurchaseStatus.ACTIVE)).thenReturn(active);
        when(purchaseItems.findByPurchaseIdIn(anyList())).thenReturn(lines);
        when(packages.findByIdWithItems(anyLong())).thenReturn(Optional.ofNullable(pkg));
    }

    @Test
    void moduleIncludedInThePackageCannotBeBoughtAgain() {
        Purchase host = paid(AppTime.nowLocal().plusDays(10));
        PlanPackage pkg = new PlanPackage();
        PlanPackageItem item = new PlanPackageItem();
        item.setProduct(waiter);
        item.setQuantity(1);
        pkg.getItems().add(item);
        catalog(host, List.of(host), List.of(), pkg);

        assertThatThrownBy(() -> service.purchaseCart(user(), cart("WAITER_PANEL"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("zaten");
        verify(purchases, never()).save(any());
    }

    @Test
    void moduleFromAnActiveAddonCannotBeBoughtAgain() {
        Purchase host = paid(AppTime.nowLocal().plusDays(10));
        Purchase addon = paid(AppTime.nowLocal().plusDays(10));
        addon.setId(2L);
        addon.setPurchaseType(PurchaseType.ADD_ON);
        PurchaseItem line = new PurchaseItem();
        line.setPurchaseId(2L);
        line.setProductId(waiter.getId());
        catalog(host, List.of(host, addon), List.of(line), new PlanPackage());

        assertThatThrownBy(() -> service.purchaseCart(user(), cart("WAITER_PANEL"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void consumableCreditsCanAlwaysBeTopped() {
        Purchase host = paid(AppTime.nowLocal().plusDays(10));
        PlanPackage pkg = new PlanPackage();
        PlanPackageItem item = new PlanPackageItem();
        item.setProduct(credits);
        item.setQuantity(3);
        pkg.getItems().add(item);
        catalog(host, List.of(host), List.of(), pkg);

        // Gets past the ownership check; fails later only for want of a billing setup in this test.
        assertThatThrownBy(() -> service.purchaseCart(user(), cart("SMART_REPORTING_ADDON"), "127.0.0.1"))
                .isNotInstanceOf(ConflictException.class);
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
