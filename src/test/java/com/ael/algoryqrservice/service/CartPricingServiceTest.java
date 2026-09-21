package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageAddon;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.PurchaseModuleLineRequest;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.repository.PlanPackageAddonRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartPricingServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PlanPackageAddonRepository planPackageAddonRepository;

    private CartPricingService cartPricingService;
    private PlanPackage planPackage;
    private Product branchModule;

    @BeforeEach
    void setUp() {
        cartPricingService = new CartPricingService(
                productRepository,
                planPackageAddonRepository,
                new PackagePricingService()
        );

        planPackage = PlanPackage.builder()
                .id(7L)
                .code("STARTER_PACKAGE")
                .name("Baslangic")
                .price(new BigDecimal("1000.00"))
                .yearlyPrice(new BigDecimal("10000.00"))
                .currency("TRY")
                .build();

        branchModule = product(11L, "QR_BRANCH", "Ek sube", "500.00", ProductBillingType.RECURRING);
        allowModules(branchModule);
        when(productRepository.findByCode("QR_BRANCH")).thenReturn(Optional.of(branchModule));
    }

    private Product product(Long id, String code, String name, String unitPrice, ProductBillingType billingType) {
        return Product.builder()
                .id(id)
                .code(code)
                .name(name)
                .scopeCode(code + "_OWNER")
                .unitPrice(new BigDecimal(unitPrice))
                .vatRate(new BigDecimal("20.00"))
                .addonPurchasable(true)
                .billingType(billingType)
                .active(true)
                .build();
    }

    private void allowModules(Product... products) {
        when(planPackageAddonRepository.findActiveByPackageId(7L)).thenReturn(
                List.of(products).stream()
                        .map(product -> PlanPackageAddon.builder().planPackage(planPackage).product(product).build())
                        .toList()
        );
    }

    private static List<PurchaseModuleLineRequest> lines(String code, int quantity) {
        return List.of(new PurchaseModuleLineRequest(code, quantity));
    }

    @Test
    void price_whenNoModules_thenGrandTotalIsPackageBase() {
        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.MONTHLY, List.of());

        assertThat(pricing.basePrice()).isEqualByComparingTo("1000.00");
        assertThat(pricing.modulesTotal()).isEqualByComparingTo("0.00");
        assertThat(pricing.grandTotal()).isEqualByComparingTo("1000.00");
        assertThat(pricing.recurringPrice()).isEqualByComparingTo("1000.00");
        assertThat(pricing.hasModules()).isFalse();
    }

    @Test
    void price_whenMonthlyModule_thenAddsVatInclusiveLine() {
        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 2));

        // 500 x 2 = 1000 net, %20 KDV = 200, satir toplami 1200.
        assertThat(pricing.modulesTotal()).isEqualByComparingTo("1200.00");
        assertThat(pricing.grandTotal()).isEqualByComparingTo("2200.00");
        assertThat(pricing.lines()).hasSize(1);
        assertThat(pricing.lines().getFirst().lineSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(pricing.lines().getFirst().lineVat()).isEqualByComparingTo("200.00");
    }

    @Test
    void price_whenYearly_thenModuleLineIsMultipliedByTwelve() {
        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.YEARLY, lines("QR_BRANCH", 1));

        assertThat(pricing.basePrice()).isEqualByComparingTo("10000.00");
        assertThat(pricing.modulesTotal()).isEqualByComparingTo("7200.00");
        assertThat(pricing.grandTotal()).isEqualByComparingTo("17200.00");
    }

    @Test
    void price_whenRecurringModule_thenRecurringPriceIncludesIt() {
        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 1));

        assertThat(pricing.recurringPrice()).isEqualByComparingTo(pricing.grandTotal());
    }

    @Test
    void price_whenModuleUnknown_thenRejects() {
        when(productRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("NOPE", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Modul bulunamadi");
    }

    @Test
    void price_whenModuleNotAllowedForPackage_thenRejects() {
        Product foreign = product(99L, "OTHER_MODULE", "Baska", "100.00", ProductBillingType.RECURRING);
        when(productRepository.findByCode("OTHER_MODULE")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() ->
                cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("OTHER_MODULE", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("secilen pakete eklenemez");
    }

    @Test
    void price_whenModuleNotAddonPurchasable_thenRejects() {
        branchModule.setAddonPurchasable(false);

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tekil olarak satin alinamaz");
    }

    @Test
    void price_whenModuleInactive_thenRejects() {
        branchModule.setActive(false);

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aktif degil");
    }

    @Test
    void price_whenModuleIsOneTime_thenChargedNowButExcludedFromRenewal() {
        branchModule.setBillingType(ProductBillingType.ONE_TIME);

        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 1));

        assertThat(pricing.grandTotal()).isEqualByComparingTo("1600.00");
        assertThat(pricing.recurringPrice()).isEqualByComparingTo("1000.00");
        assertThat(pricing.lines().getFirst().isRecurring()).isFalse();
    }

    @Test
    void price_whenYearlyAndModuleIsOneTime_thenNotMultipliedByTwelve() {
        branchModule.setBillingType(ProductBillingType.ONE_TIME);

        CartPricingService.CartPricing pricing =
                cartPricingService.price(planPackage, BillingPeriod.YEARLY, lines("QR_BRANCH", 1));

        assertThat(pricing.modulesTotal()).isEqualByComparingTo("600.00");
        assertThat(pricing.grandTotal()).isEqualByComparingTo("10600.00");
        assertThat(pricing.recurringPrice()).isEqualByComparingTo("10000.00");
    }

    @Test
    void price_whenSameModuleTwice_thenRejects() {
        List<PurchaseModuleLineRequest> duplicated = List.of(
                new PurchaseModuleLineRequest("QR_BRANCH", 1),
                new PurchaseModuleLineRequest("QR_BRANCH", 2)
        );

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.MONTHLY, duplicated))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("birden fazla kez eklenemez");
    }

    @Test
    void price_whenPackageAlreadyIncludesModuleAsUnlimited_thenRejects() {
        planPackage.setItems(List.of(
                PlanPackageItem.builder().product(branchModule).quantity(0).unlimited(true).build()
        ));

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.MONTHLY, lines("QR_BRANCH", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sinirsiz olarak zaten dahil");
    }

    @Test
    void price_whenYearlyPriceMissing_thenRejects() {
        planPackage.setYearlyPrice(null);

        assertThatThrownBy(() -> cartPricingService.price(planPackage, BillingPeriod.YEARLY, List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Paket fiyati gecersiz");
    }
}
