package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackagePricingServiceTest {

    private final PackagePricingService pricingService = new PackagePricingService();

    @Test
    void calculate_whenTwoProducts_thenSubtotalPlusVatEqualsTotal() {
        Product qr = Product.builder()
                .id(1L)
                .code("QR")
                .name("QR")
                .unitPrice(new BigDecimal("100.00"))
                .vatRate(new BigDecimal("20.00"))
                .build();
        Product menu = Product.builder()
                .id(2L)
                .code("MENU")
                .name("Menu")
                .unitPrice(new BigDecimal("50.00"))
                .vatRate(new BigDecimal("20.00"))
                .build();

        List<PlanPackageItem> items = List.of(
                PlanPackageItem.builder().product(qr).quantity(2).unlimited(false).build(),
                PlanPackageItem.builder().product(menu).quantity(1).unlimited(true).build()
        );

        PackagePricingService.PriceBreakdown breakdown = pricingService.calculate(items);

        assertThat(breakdown.subtotal()).isEqualByComparingTo("250.00");
        assertThat(breakdown.vatAmount()).isEqualByComparingTo("50.00");
        assertThat(breakdown.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void applyTo_whenPackageHasItems_thenPersistBreakdownFields() {
        Product product = Product.builder()
                .id(1L)
                .unitPrice(new BigDecimal("100.00"))
                .vatRate(new BigDecimal("20.00"))
                .build();
        PlanPackage planPackage = PlanPackage.builder()
                .items(List.of(PlanPackageItem.builder().product(product).quantity(1).unlimited(false).build()))
                .build();

        pricingService.applyTo(planPackage);

        assertThat(planPackage.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(planPackage.getVatAmount()).isEqualByComparingTo("20.00");
        assertThat(planPackage.getPrice()).isEqualByComparingTo("120.00");
    }
}
