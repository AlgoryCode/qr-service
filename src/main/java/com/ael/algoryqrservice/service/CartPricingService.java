package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.PurchaseModuleLineRequest;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.repository.PlanPackageAddonRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Prices a cart: the package base amount for the chosen billing period plus the optional
 * module lines the buyer attached, all charged in a single payment.
 *
 * <p>A YEARLY cart multiplies each module line by 12 so a module costs the same per month
 * regardless of the billing period. {@code recurringPrice} is what a renewal must charge:
 * the base plus only the {@link ProductBillingType#RECURRING} lines. ONE_TIME lines are
 * therefore paid once and dropped from the renewal amount.
 */
@Service
@RequiredArgsConstructor
public class CartPricingService {

    private static final int YEARLY_MULTIPLIER = 12;

    private final ProductRepository productRepository;
    private final PlanPackageAddonRepository planPackageAddonRepository;
    private final PackagePricingService packagePricingService;

    public record ModuleLine(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal lineSubtotal,
            BigDecimal lineVat,
            BigDecimal lineTotal,
            ProductBillingType billingType
    ) {
        public boolean isRecurring() {
            return billingType == ProductBillingType.RECURRING;
        }
    }

    public record CartPricing(
            BigDecimal basePrice,
            BigDecimal modulesTotal,
            BigDecimal grandTotal,
            BigDecimal recurringPrice,
            List<ModuleLine> lines
    ) {
        public boolean hasModules() {
            return !lines.isEmpty();
        }
    }

    public CartPricing price(PlanPackage planPackage, BillingPeriod billingPeriod, List<PurchaseModuleLineRequest> requestedModules) {
        BigDecimal basePrice = resolveBasePrice(planPackage, billingPeriod);
        List<ModuleLine> lines = resolveLines(planPackage, billingPeriod, requestedModules);

        BigDecimal modulesTotal = BigDecimal.ZERO;
        BigDecimal recurringModulesTotal = BigDecimal.ZERO;
        for (ModuleLine line : lines) {
            modulesTotal = modulesTotal.add(line.lineTotal());
            if (line.isRecurring()) {
                recurringModulesTotal = recurringModulesTotal.add(line.lineTotal());
            }
        }

        return new CartPricing(
                scale(basePrice),
                scale(modulesTotal),
                scale(basePrice.add(modulesTotal)),
                scale(basePrice.add(recurringModulesTotal)),
                List.copyOf(lines)
        );
    }

    private BigDecimal resolveBasePrice(PlanPackage planPackage, BillingPeriod billingPeriod) {
        BigDecimal basePrice = billingPeriod == BillingPeriod.YEARLY
                ? planPackage.effectiveYearlyPrice()
                : planPackage.effectiveMonthlyPrice();
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Paket fiyati gecersiz");
        }
        return basePrice;
    }

    private List<ModuleLine> resolveLines(
            PlanPackage planPackage,
            BillingPeriod billingPeriod,
            List<PurchaseModuleLineRequest> requestedModules
    ) {
        if (requestedModules == null || requestedModules.isEmpty()) {
            return List.of();
        }

        Set<Long> allowedProductIds = planPackageAddonRepository.findActiveByPackageId(planPackage.getId()).stream()
                .map(addon -> addon.getProduct().getId())
                .collect(Collectors.toSet());
        Set<Long> unlimitedInPackage = new HashSet<>();
        for (PlanPackageItem item : planPackage.getItems()) {
            if (item.isUnlimited()) {
                unlimitedInPackage.add(item.getProduct().getId());
            }
        }

        Set<String> seenCodes = new HashSet<>();
        List<ModuleLine> lines = new ArrayList<>();
        for (PurchaseModuleLineRequest requested : requestedModules) {
            String productCode = requested.resolvedProductCode();
            if (productCode == null || productCode.isBlank()) {
                throw new BadRequestException("Urun kodu zorunludur");
            }
            if (!seenCodes.add(productCode)) {
                throw new BadRequestException("Ayni modul birden fazla kez eklenemez: " + productCode);
            }

            Product product = productRepository.findByCode(productCode)
                    .orElseThrow(() -> new BadRequestException("Modul bulunamadi: " + productCode));
            if (!product.isActive()) {
                throw new BadRequestException("Modul aktif degil: " + productCode);
            }
            if (!product.isAddonPurchasable()) {
                throw new BadRequestException("Bu modul tekil olarak satin alinamaz: " + productCode);
            }
            if (!allowedProductIds.contains(product.getId())) {
                throw new BadRequestException("Bu modul secilen pakete eklenemez: " + productCode);
            }
            if (unlimitedInPackage.contains(product.getId())) {
                throw new BadRequestException("Bu modul pakette sinirsiz olarak zaten dahil: " + productCode);
            }

            ProductBillingType billingType = product.getBillingType() == null
                    ? ProductBillingType.RECURRING
                    : product.getBillingType();

            PackagePricingService.LinePrice line = packagePricingService.calculateProduct(product, requested.resolvedQuantity());
            if (line.lineTotal().signum() <= 0) {
                throw new BadRequestException("Modul fiyati gecersiz: " + productCode);
            }

            // Tekrarlayan modul yillik donemde 12 ay boyunca gecerlidir; tek seferlik
            // modul ise donemden bagimsiz olarak bir kez tahsil edilir.
            boolean scaleToPeriod = billingPeriod == BillingPeriod.YEARLY
                    && billingType == ProductBillingType.RECURRING;
            BigDecimal multiplier = BigDecimal.valueOf(scaleToPeriod ? YEARLY_MULTIPLIER : 1);
            lines.add(new ModuleLine(
                    product,
                    requested.resolvedQuantity(),
                    line.unitPrice(),
                    line.vatRate(),
                    scale(line.lineSubtotal().multiply(multiplier)),
                    scale(line.lineVat().multiply(multiplier)),
                    scale(line.lineTotal().multiply(multiplier)),
                    billingType
            ));
        }
        return lines;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }
}
