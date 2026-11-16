package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidUsageRequestException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.QuotaExceededException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.CatalogProduct;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ListedEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductAccessView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.ProductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
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

    @Transactional(readOnly = true)
    public List<ListedEntitlement> listActive(Long merchantId) {
        List<UserEntitlement> entitlements = entitlementPort.findActiveByMerchantId(merchantId);
        Map<Long, CatalogProduct> products = productPort.findByIds(productIds(entitlements));
        return entitlements.stream()
                .map(entitlement -> new ListedEntitlement(entitlement, productCode(entitlement, products)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductAccessView productAccess(Long merchantId, String productCode) {
        Optional<ActivePackage> active = fulfillmentRepository.findActiveByMerchantId(merchantId)
                .filter(ActivePackage::grantsAccess);
        if (active.isEmpty()) {
            return ProductAccessView.denied(null);
        }
        return match(merchantId, productCode)
                .map(entitlement -> allowed(active.get(), entitlement))
                .orElseGet(() -> ProductAccessView.denied(active.get().packageCode()));
    }

    @Transactional
    public UserEntitlement consume(Long merchantId, String productCode, int quantity) {
        requireQuantity(quantity);
        List<UserEntitlement> candidates = candidates(merchantId, productCode, true);
        if (candidates.isEmpty()) {
            throw new QuotaExceededException("Product is not in the active package");
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
            throw new QuotaExceededException("Entitlement quota exceeded");
        }
        return entitlementPort.save(copyUsed(entitlement, used));
    }

    @Transactional
    public Optional<UserEntitlement> release(Long merchantId, String productCode, int quantity) {
        requireQuantity(quantity);
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
