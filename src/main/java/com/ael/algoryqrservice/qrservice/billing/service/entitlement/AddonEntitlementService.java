package com.ael.algoryqrservice.qrservice.billing.service.entitlement;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.PurchaseItem;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseItemRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a paid add-on purchase into spendable entitlements (e.g. N extra smart reports), and takes
 * them back when the purchase is refunded or cancelled. Add-ons must never go through the package
 * fulfillment path: that would supersede the merchant's package with the add-on's code.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddonEntitlementService {

    private final FulfillmentService fulfillmentService;
    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductRepository productRepository;

    public static boolean isAddon(Purchase purchase) {
        return purchase != null && purchase.getPurchaseType() == PurchaseType.ADD_ON;
    }

    public void grant(Purchase purchase) {
        if (!isAddon(purchase) || purchase.getId() == null || purchase.getMerchantId() == null) {
            return;
        }
        List<AddonGrant.Line> lines = lines(purchase);
        fulfillmentService.grantAddon(new AddonGrant(purchase.getMerchantId(), purchase.getId(), lines));
    }

    public void revoke(Purchase purchase) {
        if (!isAddon(purchase) || purchase.getId() == null) {
            return;
        }
        fulfillmentService.revokeAddon(purchase.getId());
    }

    /** Pushes the add-on's entitlements to the purchase's current end date. */
    public void extend(Purchase purchase) {
        if (!isAddon(purchase) || purchase.getId() == null || purchase.getExpiresAt() == null) {
            return;
        }
        fulfillmentService.extendAddon(purchase.getId(), purchase.getExpiresAt().atZone(AppTime.ZONE).toInstant());
    }

    private List<AddonGrant.Line> lines(Purchase purchase) {
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(purchase.getId());
        if (items.isEmpty()) {
            // Single-product add-ons bought before line items existed.
            return Optional.ofNullable(purchase.getProductId())
                    .flatMap(productRepository::findById)
                    .map(product -> List.of(line(product, purchase.getAddonQuantity(), purchase)))
                    .orElseGet(List::of);
        }
        Map<Long, Product> products = productRepository.findAllById(
                        items.stream().map(PurchaseItem::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return items.stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> line(products.get(item.getProductId()), item.getQuantity(), purchase))
                .toList();
    }

    private static AddonGrant.Line line(Product product, Integer quantity, Purchase purchase) {
        String featureCode = product.getFeatureCode() != null && !product.getFeatureCode().isBlank()
                ? product.getFeatureCode()
                : product.getCode();
        return new AddonGrant.Line(
                product.getId(),
                featureCode,
                product.getScopeCode(),
                quantity == null ? 0 : quantity,
                expiresAt(product, purchase)
        );
    }

    /** One-time consumables (report credits) stay until used; anything else lives as long as the purchase. */
    private static Instant expiresAt(Product product, Purchase purchase) {
        boolean oneTime = product.getBillingType() == null || product.getBillingType() == ProductBillingType.ONE_TIME;
        if (product.isConsumable() && oneTime) {
            return null;
        }
        return purchase.getExpiresAt() == null ? null : purchase.getExpiresAt().atZone(AppTime.ZONE).toInstant();
    }
}
