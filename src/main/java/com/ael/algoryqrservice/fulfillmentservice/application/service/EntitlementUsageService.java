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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Transactional(
            readOnly = true,
            transactionManager = "fulfillmentTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public List<ListedEntitlement> listActive(Long userId) {
        List<UserEntitlement> entitlements = entitlementPort.findActiveByUserId(userId);
        Map<Long, CatalogProduct> products = productPort.findByIds(productIds(entitlements));
        return entitlements.stream()
                .map(entitlement -> new ListedEntitlement(entitlement, productCode(entitlement, products)))
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public ProductAccessView productAccess(Long userId, String productCode) {
        Optional<ActivePackage> active = fulfillmentRepository.findActiveByUserId(userId);
        if (active.isEmpty()) {
            return ProductAccessView.denied(null);
        }
        return match(userId, productCode)
                .map(entitlement -> allowed(active.get(), entitlement))
                .orElseGet(() -> ProductAccessView.denied(active.get().packageCode()));
    }

    @Transactional(transactionManager = "fulfillmentTransactionManager")
    public UserEntitlement consume(Long userId, String productCode, int quantity) {
        requireQuantity(quantity);
        UserEntitlement entitlement = requireMatch(userId, productCode);
        if (entitlement.unlimited()) {
            return entitlement;
        }
        int used = usedOf(entitlement) + quantity;
        if (used > limitOf(entitlement)) {
            throw new QuotaExceededException("Entitlement quota exceeded");
        }
        return entitlementPort.save(copyUsed(entitlement, used));
    }

    @Transactional(transactionManager = "fulfillmentTransactionManager")
    public Optional<UserEntitlement> release(Long userId, String productCode, int quantity) {
        requireQuantity(quantity);
        Optional<UserEntitlement> match = match(userId, productCode);
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

    private UserEntitlement requireMatch(Long userId, String productCode) {
        return match(userId, productCode)
                .orElseThrow(() -> new QuotaExceededException("Product is not in the active package"));
    }

    private Optional<UserEntitlement> match(Long userId, String productCode) {
        if (fulfillmentRepository.findActiveByUserId(userId).isEmpty()) {
            return Optional.empty();
        }
        Optional<CatalogProduct> product = productPort.findByCode(productCode);
        return entitlementPort.findActiveByUserId(userId).stream()
                .filter(entitlement -> matches(entitlement, productCode, product))
                .findFirst();
    }

    private boolean matches(UserEntitlement entitlement, String productCode, Optional<CatalogProduct> product) {
        if (productCode.equals(entitlement.featureCode())) {
            return true;
        }
        if (product.isEmpty()) {
            return false;
        }
        CatalogProduct catalogProduct = product.get();
        if (entitlement.productId() != null && entitlement.productId().equals(catalogProduct.id())) {
            return true;
        }
        return catalogProduct.featureCode() != null && catalogProduct.featureCode().equals(entitlement.featureCode());
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
                entitlement.userId(),
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
