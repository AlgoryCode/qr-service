package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductUsageGateway {

    private static final String ACTIVE = "ACTIVE";

    private final ObjectProvider<FulfillmentServiceClient> fulfillmentClients;

    public void allow(Long userId, String productCode) {
        ExternalProductAccessResponse access = readAccess(userId, productCode);
        if (!access.allowed()) {
            deny(access);
        }
    }

    public ExternalConsumeResponse use(Long userId, String productCode, int quantity) {
        FulfillmentServiceClient client = client();
        ExternalProductAccessResponse access = client.findProductAccess(userId, productCode);
        if (!access.allowed()) {
            deny(access);
        }
        if (quantity <= 0 || !countable(client, userId, productCode)) {
            return null;
        }
        try {
            return client.consume(userId, productCode, quantity);
        } catch (FulfillmentQuotaExceededException exception) {
            throw quotaExceeded(productCode);
        }
    }

    private ExternalProductAccessResponse readAccess(Long userId, String productCode) {
        return client().findProductAccess(userId, productCode);
    }

    private boolean countable(FulfillmentServiceClient client, Long userId, String productCode) {
        return client.listEntitlements(userId).stream()
                .filter(item -> matches(item, productCode))
                .filter(this::open)
                .anyMatch(item -> !item.unlimited());
    }

    private boolean matches(ExternalEntitlementResponse item, String productCode) {
        return productCode.equals(item.productCode()) || productCode.equals(item.featureCode());
    }

    private boolean open(ExternalEntitlementResponse item) {
        if (item.status() != null && !ACTIVE.equals(item.status())) {
            return false;
        }
        return item.expiresAt() == null || item.expiresAt().isAfter(Instant.now());
    }

    private FulfillmentServiceClient client() {
        FulfillmentServiceClient client = fulfillmentClients.getIfAvailable();
        if (client == null) {
            throw new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
        }
        return client;
    }

    private void deny(ExternalProductAccessResponse access) {
        if (access.packageCode() == null || access.packageCode().isBlank()) {
            throw new ForbiddenException("Bu ürün için aktif paket gerekli");
        }
        throw new ProductNotInPackageException(access.packageCode());
    }

    private ForbiddenException quotaExceeded(String productCode) {
        if (CatalogProducts.QR_BRANCH.equals(productCode)) {
            return new ForbiddenException(
                    "EXTRA_BRANCH_REQUIRED",
                    "Ek şube ücretlidir. Lütfen ek şube hakkı satın alın."
            );
        }
        return new ForbiddenException(
                "Yetersiz veya süresi dolmuş " + productCode + " hakkı. Lütfen paket satın alın."
        );
    }

    static final class ProductNotInPackageException extends ForbiddenException {

        private final String packageCode;

        private ProductNotInPackageException(String packageCode) {
            super(AccessSessionMapper.PRODUCT_NOT_IN_PACKAGE, "Ürün aktif pakette yok");
            this.packageCode = packageCode;
        }

        String packageCode() {
            return packageCode;
        }
    }
}
