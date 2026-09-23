package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RemoteEntitlementGate {

    private static final String ACTIVE = "ACTIVE";
    private static final String MISSING_SCOPE_MESSAGE_SUFFIX = " yetkisi için uygun paket gerekli";
    private static final String MENU_PRODUCT_LIMIT_MESSAGE =
            "Yetersiz menü ürün hakkı. Paket limitinize ulaştınız veya paket satın almanız gerekiyor.";
    private static final Map<String, String> EXHAUSTED_QUOTA_MESSAGES = Map.of(
            CatalogProducts.QR_MENU,
            "Yetersiz dijital menü hakkı. Lütfen paket satın alın veya mevcut bir menüyü pasif yaparak slot açın.",
            CatalogProducts.MENU_PRODUCT,
            "Yetersiz menü ürün hakkı. Lütfen paket satın alın veya paketinizi yükseltin."
    );

    private final ProductRepository productRepository;

    public boolean hasScope(FulfillmentServiceClient client, Long userId, String scopeCode) {
        return client.listEntitlements(userId).stream()
                .anyMatch(entitlement -> scopeCode.equals(entitlement.scopeCode()) && open(entitlement));
    }

    public void requireScope(FulfillmentServiceClient client, Long userId, String scopeCode) {
        if (!hasScope(client, userId, scopeCode)) {
            throw new ForbiddenException(scopeCode + MISSING_SCOPE_MESSAGE_SUFFIX);
        }
    }

    public ConsumedEntitlement consume(FulfillmentServiceClient client, Long userId, String productCode, int amount) {
        Optional<Product> product = findProduct(productCode);
        String featureCode = resolveFeatureCode(product, productCode);
        if (product.filter(candidate -> !candidate.isConsumable()).isPresent()) {
            requireScope(client, userId, product.get().getScopeCode());
            return null;
        }
        try {
            ExternalConsumeResponse response = client.consume(userId, featureCode, amount);
            return new ConsumedEntitlement(response.purchaseId(), response.entitlementId(), amount);
        } catch (FulfillmentQuotaExceededException exception) {
            throw new ForbiddenException(exhaustedQuotaMessage(featureCode));
        }
    }

    public void release(FulfillmentServiceClient client, Long userId, String productCode, int amount) {
        Optional<Product> product = findProduct(productCode);
        if (product.filter(candidate -> !candidate.isConsumable()).isPresent()) {
            return;
        }
        client.release(userId, resolveFeatureCode(product, productCode), amount);
    }

    public void assertMenuQuota(FulfillmentServiceClient client, Long userId, int additionalProducts) {
        if (remaining(client, userId, CatalogProducts.MENU_PRODUCT) < additionalProducts) {
            throw new ForbiddenException(MENU_PRODUCT_LIMIT_MESSAGE);
        }
    }

    public int remaining(FulfillmentServiceClient client, Long userId, String productCode) {
        return client.listEntitlements(userId).stream()
                .filter(entitlement -> matches(entitlement, productCode) && open(entitlement))
                .mapToInt(this::remainingOf)
                .max()
                .orElse(0);
    }

    private boolean matches(ExternalEntitlementResponse entitlement, String productCode) {
        return productCode.equals(entitlement.productCode()) || productCode.equals(entitlement.featureCode());
    }

    private boolean open(ExternalEntitlementResponse entitlement) {
        if (entitlement.status() != null && !ACTIVE.equals(entitlement.status())) {
            return false;
        }
        Instant expiresAt = entitlement.expiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    private int remainingOf(ExternalEntitlementResponse entitlement) {
        if (entitlement.unlimited()) {
            return Integer.MAX_VALUE;
        }
        int total = entitlement.quantity() == null ? 0 : entitlement.quantity();
        int used = entitlement.usedQuantity() == null ? 0 : entitlement.usedQuantity();
        return Math.max(0, total - used);
    }

    private Optional<Product> findProduct(String productCode) {
        return productCode == null ? Optional.empty() : productRepository.findByCode(productCode);
    }

    private String resolveFeatureCode(Optional<Product> product, String productCode) {
        return product
                .map(candidate -> isBlank(candidate.getFeatureCode()) ? candidate.getCode() : candidate.getFeatureCode())
                .orElse(productCode);
    }

    private String exhaustedQuotaMessage(String featureCode) {
        return EXHAUSTED_QUOTA_MESSAGES.getOrDefault(
                featureCode,
                "Yetersiz veya süresi dolmuş " + featureCode + " hakkı. Lütfen paket satın alın."
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
