package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PackagePricingService {

    public static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("20.00");

    public record LinePrice(
            Long productId,
            String productCode,
            String productName,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            int billableQuantity,
            BigDecimal lineSubtotal,
            BigDecimal lineVat,
            BigDecimal lineTotal
    ) {
    }

    public record PriceBreakdown(
            BigDecimal subtotal,
            BigDecimal vatAmount,
            BigDecimal total,
            List<LinePrice> lines
    ) {
        public static PriceBreakdown zero() {
            return new PriceBreakdown(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), List.of());
        }
    }

    public void applyTo(PlanPackage planPackage) {
        PriceBreakdown breakdown = calculate(planPackage.getItems());
        planPackage.setSubtotal(breakdown.subtotal());
        planPackage.setVatAmount(breakdown.vatAmount());
        planPackage.setPrice(breakdown.total());
    }

    public PriceBreakdown calculate(List<PlanPackageItem> items) {
        if (items == null || items.isEmpty()) {
            return PriceBreakdown.zero();
        }
        List<LinePrice> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;

        for (PlanPackageItem item : items) {
            Product product = item.getProduct();
            BigDecimal unitPrice = normalizeMoney(product.getUnitPrice());
            BigDecimal vatRate = product.getVatRate() == null ? DEFAULT_VAT_RATE : normalizeRate(product.getVatRate());
            int billableQuantity = item.isUnlimited() ? 1 : Math.max(item.getQuantity() == null ? 0 : item.getQuantity(), 0);
            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(billableQuantity)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineSubtotal.multiply(vatRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineVat);

            lines.add(new LinePrice(
                    product.getId(),
                    product.getCode(),
                    product.getName(),
                    unitPrice,
                    vatRate,
                    billableQuantity,
                    lineSubtotal,
                    lineVat,
                    lineTotal
            ));
            subtotal = subtotal.add(lineSubtotal);
            vatAmount = vatAmount.add(lineVat);
        }

        BigDecimal total = subtotal.add(vatAmount).setScale(2, RoundingMode.HALF_UP);
        return new PriceBreakdown(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                vatAmount.setScale(2, RoundingMode.HALF_UP),
                total,
                List.copyOf(lines)
        );
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
