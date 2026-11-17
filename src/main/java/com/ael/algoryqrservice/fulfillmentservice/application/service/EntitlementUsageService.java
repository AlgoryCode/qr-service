package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidUsageRequestException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.QuotaExceededException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.CatalogProduct;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ListedEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductAccessView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.CountedResourcePort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.ProductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EntitlementUsageService {

    private final FulfillmentRepositoryPort fulfillmentRepository;
    private final EntitlementPort entitlementPort;
    private final ProductPort productPort;
    private final CountedResourcePort countedResources;
    private final com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService snapshots;

    @Transactional(readOnly = true)
    public List<ListedEntitlement> listActive(Long merchantId) {
        List<UserEntitlement> entitlements = withLiveUsage(merchantId, entitlementPort.findActiveByMerchantId(merchantId));
        Map<Long, CatalogProduct> products = productPort.findByIds(productIds(entitlements));
        return entitlements.stream()
                .map(entitlement -> new ListedEntitlement(entitlement, productCode(entitlement, products)))
                .toList();
    }

    /**
     * Whether the merchant may use {@code productCode} now. Read from the entitlement snapshot: one
     * row per feature, already summed over every open grant and closed when the package is.
     */
    @Transactional(readOnly = true)
    public ProductAccessView productAccess(Long merchantId, String productCode) {
        Optional<ActivePackage> active = fulfillmentRepository.findActiveByMerchantId(merchantId)
                .filter(ActivePackage::grantsAccess);
        if (active.isEmpty()) {
            return ProductAccessView.denied(null);
        }
        Optional<CatalogProduct> product = productPort.findByCode(productCode);
        String feature = product.map(CatalogProduct::featureCode)
                .filter(code -> code != null && !code.isBlank())
                .orElse(productCode);
        String scope = product.map(CatalogProduct::scopeCode).orElse(null);
        return snapshots.read(merchantId).feature(feature)
                .map(effective -> new ProductAccessView(true, active.get().packageCode(), feature, scope))
                .orElseGet(() -> ProductAccessView.denied(active.get().packageCode()));
    }

    @Transactional
    public UserEntitlement consume(Long merchantId, String productCode, int quantity) {
        requireQuantity(quantity);
        List<UserEntitlement> candidates = candidates(merchantId, productCode, true);
        if (candidates.isEmpty()) {
            throw new QuotaExceededException("Product is not in the active package");
        }
        String counted = countedFeature(productCode);
        if (counted != null) {
            return checkCapacity(merchantId, counted, candidates, quantity);
        }
        // Spend from the first entitlement that can still cover the request (e.g. a second add-on
        // purchase once the first is used up); otherwise the best match reports the overflow.
        UserEntitlement entitlement = candidates.stream()
                .filter(candidate -> candidate.unlimited() || usedOf(candidate) + quantity <= limitOf(candidate))
                .findFirst()
                .orElse(candidates.get(0));
        if (entitlement.unlimited()) {
            return entitlement;
        }
        int used = usedOf(entitlement) + quantity;
        if (used > limitOf(entitlement)) {
            throw new QuotaExceededException("Entitlement quota exceeded",
                    entitlement.featureCode(), (long) limitOf(entitlement), (long) usedOf(entitlement));
        }
        return entitlementPort.save(copyUsed(entitlement, used));
    }

    /**
     * Capacity features (branches, menus, products, QR codes) are checked against what exists right
     * now: every open grant for the feature adds to the limit, and nothing is written. The caller's
     * rows are locked, so two concurrent creates in one transaction each see the other's result.
     */
    private UserEntitlement checkCapacity(Long merchantId, String feature, List<UserEntitlement> grants, int quantity) {
        Optional<UserEntitlement> unlimited = grants.stream().filter(UserEntitlement::unlimited).findFirst();
        if (unlimited.isPresent()) {
            return unlimited.get();
        }
        long limit = grants.stream().mapToLong(this::limitOf).sum();
        long used = countedResources.count(merchantId, feature).orElse(0);
        if (used + quantity > limit) {
            throw new QuotaExceededException("Entitlement quota exceeded", feature, limit, used);
        }
        return copyUsed(grants.get(0), (int) Math.min(used + quantity, Integer.MAX_VALUE));
    }

    /** Capacity usage is live, so a delete has nothing to give back. Credits and meters still do. */
    @Transactional
    public Optional<UserEntitlement> release(Long merchantId, String productCode, int quantity) {
        requireQuantity(quantity);
        if (countedFeature(productCode) != null) {
            return Optional.empty();
        }
        List<UserEntitlement> candidates = candidates(merchantId, productCode, true);
        Optional<UserEntitlement> match = candidates.stream()
                .filter(candidate -> !candidate.unlimited() && usedOf(candidate) > 0)
                .findFirst()
                .or(() -> candidates.stream().findFirst());
        if (match.isEmpty() || match.get().unlimited()) {
            return match;
        }
        int used = Math.max(0, usedOf(match.get()) - quantity);
        if (used == usedOf(match.get())) {
            return match;
        }
        return Optional.of(entitlementPort.save(copyUsed(match.get(), used)));
    }

    /**
     * Fills each capacity row's used count from the live total, in grant order (package lines first,
     * then add-ons), so screens and "remaining" math read real numbers without a stored counter.
     * Whatever exceeds every row (after a downgrade) stays on the last one.
     */
    private List<UserEntitlement> withLiveUsage(Long merchantId, List<UserEntitlement> rows) {
        Map<String, Long> live = new HashMap<>();
        List<UserEntitlement> result = new ArrayList<>(rows.size());
        Map<String, Integer> lastIndex = new HashMap<>();
        for (UserEntitlement row : rows) {
            String feature = row.featureCode();
            if (!countedResources.isCounted(feature)) {
                result.add(row);
                continue;
            }
            long left = live.computeIfAbsent(feature, key -> countedResources.count(merchantId, key).orElse(0));
            long take = row.unlimited() ? left : Math.min(left, limitOf(row));
            live.put(feature, left - take);
            lastIndex.put(feature, result.size());
            result.add(copyUsed(row, (int) Math.min(take, Integer.MAX_VALUE)));
        }
        live.forEach((feature, left) -> {
            Integer index = lastIndex.get(feature);
            if (left > 0 && index != null) {
                UserEntitlement last = result.get(index);
                result.set(index, copyUsed(last, (int) Math.min(usedOf(last) + left, Integer.MAX_VALUE)));
            }
        });
        return result;
    }

    private String countedFeature(String productCode) {
        if (countedResources.isCounted(productCode)) {
            return productCode;
        }
        return productPort.findByCode(productCode)
                .map(CatalogProduct::featureCode)
                .filter(countedResources::isCounted)
                .orElse(null);
    }

    private ProductAccessView allowed(ActivePackage active, UserEntitlement entitlement) {
        return new ProductAccessView(true, active.packageCode(), entitlement.featureCode(), entitlement.scopeCode());
    }

    private Optional<UserEntitlement> match(Long merchantId, String productCode) {
        return candidates(merchantId, productCode, false).stream().findFirst();
    }

    /**
     * Active entitlements that grant {@code productCode}, most specific first: an exact feature code,
     * then the purchased product itself, then any entitlement sharing the product's feature. Without
     * this order an add-on (e.g. SMART_REPORTING_ADDON, feature SMART_REPORTING) could resolve to the
     * package's unlimited SMART_REPORTING row and never be decremented.
     */
    private List<UserEntitlement> candidates(Long merchantId, String productCode, boolean forUpdate) {
        if (fulfillmentRepository.findActiveByMerchantId(merchantId).filter(ActivePackage::grantsAccess).isEmpty()) {
            return List.of();
        }
        Optional<CatalogProduct> product = productPort.findByCode(productCode);
        // Usage changes lock the merchant's rows: two concurrent requests would otherwise both read
        // the old counter and one write would be lost, letting the merchant exceed the limit.
        List<UserEntitlement> rows = forUpdate
                ? entitlementPort.lockActiveByMerchantId(merchantId)
                : entitlementPort.findActiveByMerchantId(merchantId);
        return rows.stream()
                .filter(entitlement -> rank(entitlement, productCode, product) < NO_MATCH)
                .sorted(Comparator.comparingInt(entitlement -> rank(entitlement, productCode, product)))
                .toList();
    }

    private static final int NO_MATCH = 3;

    private static int rank(UserEntitlement entitlement, String productCode, Optional<CatalogProduct> product) {
        if (productCode.equals(entitlement.featureCode())) {
            return 0;
        }
        if (product.isEmpty()) {
            return NO_MATCH;
        }
        CatalogProduct catalogProduct = product.get();
        if (entitlement.productId() != null && entitlement.productId().equals(catalogProduct.id())) {
            return 1;
        }
        if (catalogProduct.featureCode() != null && catalogProduct.featureCode().equals(entitlement.featureCode())) {
            return 2;
        }
        return NO_MATCH;
    }

    private List<Long> productIds(List<UserEntitlement> entitlements) {
        return entitlements.stream()
                .map(UserEntitlement::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String productCode(UserEntitlement entitlement, Map<Long, CatalogProduct> products) {
        if (entitlement.productId() == null) {
            return entitlement.featureCode();
        }
        CatalogProduct product = products.get(entitlement.productId());
        if (product == null || product.code() == null || product.code().isBlank()) {
            return entitlement.featureCode();
        }
        return product.code();
    }

    private void requireQuantity(int quantity) {
        if (quantity < 1) {
            throw new InvalidUsageRequestException("Quantity must be positive");
        }
    }

    private int usedOf(UserEntitlement entitlement) {
        return entitlement.usedQuantity() == null ? 0 : entitlement.usedQuantity();
    }

    private int limitOf(UserEntitlement entitlement) {
        return entitlement.quantity() == null ? 0 : entitlement.quantity();
    }

    private UserEntitlement copyUsed(UserEntitlement entitlement, int usedQuantity) {
        return new UserEntitlement(
                entitlement.id(),
                entitlement.merchantId(),
                entitlement.fulfillmentId(),
                entitlement.purchaseId(),
                entitlement.productId(),
                entitlement.productTypeId(),
                entitlement.featureCode(),
                entitlement.scopeCode(),
                entitlement.quantity(),
                entitlement.unlimited(),
                usedQuantity,
                entitlement.source(),
                entitlement.status(),
                entitlement.startsAt(),
                entitlement.expiresAt(),
                entitlement.createdAt(),
                Instant.now()
        );
    }
}
