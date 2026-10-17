package com.ael.algoryqrservice.qrservice.billing.policy;

import com.ael.algoryqrservice.qrservice.billing.service.PackagePricingService;
import com.ael.algoryqrservice.qrservice.billing.service.UserPackageAssignmentService;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PlanPackageAssignmentPolicy {

    private static final String CURRENCY = "TRY";
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final PackagePricingService packagePricingService;

    public void requireAssignable(PlanPackage plan) {
        requireCatalog(plan);
        if (!CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(plan.getCode())) {
            requirePrice(plan, packagePricingService.calculate(plan.getItems()));
        }
    }

    public int resolvePeriodDays(PlanPackage plan, Integer overrideDays, int fallbackDays) {
        if (overrideDays != null && overrideDays >= 1) {
            return overrideDays;
        }
        if (plan.getValidityDays() != null && plan.getValidityDays() >= 1) {
            return plan.getValidityDays();
        }
        return fallbackDays;
    }

    public void requireCatalog(PlanPackage plan) {
        if (!plan.isActive()) {
            throw new BadRequestException("Paket satışa kapalı");
        }
        if (plan.getItems() == null || plan.getItems().isEmpty()) {
            throw new BadRequestException("Paket kalemleri boş");
        }
        boolean demoCatalogPackage = CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(plan.getCode());
        if (!demoCatalogPackage && (!plan.isPurchasable() || plan.isSystemManaged())) {
            throw new BadRequestException("Paket satın alınamaz");
        }
        if (plan.getValidityDays() == null || plan.getValidityDays() < 1) {
            throw new BadRequestException("Paket süresi geçersiz");
        }
        if (plan.getCurrency() == null || !CURRENCY.equals(plan.getCurrency())) {
            throw new BadRequestException("Paket para birimi TRY olmalı");
        }
        for (PlanPackageItem item : plan.getItems()) {
            Product product = item.getProduct();
            if (product == null || !product.isActive()) {
                throw new BadRequestException("Paket ürünü aktif değil");
            }
            if (product.getUnitPrice() == null) {
                throw new BadRequestException("Ürün fiyatı boş");
            }
            if (!item.isUnlimited() && (item.getQuantity() == null || item.getQuantity() <= 0)) {
                throw new BadRequestException("Ürün adedi geçersiz");
            }
        }
    }

    private void requirePrice(PlanPackage plan, PackagePricingService.PriceBreakdown breakdown) {
        for (PackagePricingService.LinePrice line : breakdown.lines()) {
            if (!same(line.lineTotal(), line.lineSubtotal().add(line.lineVat()))) {
                throw new BadRequestException(
                        UserPackageAssignmentService.PACKAGE_PRICE_MISMATCH,
                        "Paket satır tutarı uyuşmuyor"
                );
            }
        }
        if (!same(plan.getSubtotal(), breakdown.subtotal())
                || !same(plan.getVatAmount(), breakdown.vatAmount())
                || !same(plan.getPrice(), breakdown.total())) {
            throw new BadRequestException(
                    UserPackageAssignmentService.PACKAGE_PRICE_MISMATCH,
                    "Paket tutarı ürün toplamıyla uyuşmuyor"
            );
        }
    }

    private boolean same(BigDecimal stored, BigDecimal calculated) {
        if (stored == null || calculated == null) {
            return false;
        }
        return stored.subtract(calculated).abs().compareTo(TOLERANCE) <= 0;
    }
}
