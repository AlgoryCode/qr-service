package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentQuotaExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductUsageGateway {

    private static final String ACTIVE = "ACTIVE";

    private final FulfillmentServiceClient fulfillmentClient;

    public void allow(Long merchantId, String productCode) {
        ExternalProductAccessResponse access = readAccess(merchantId, productCode);
        if (!access.allowed()) {
            deny(access);
        }
    }

    public ExternalConsumeResponse use(Long merchantId, String productCode, int quantity) {
        ExternalProductAccessResponse access = fulfillmentClient.findProductAccess(merchantId, productCode);
        if (!access.allowed()) {
            deny(access);
        }
        if (quantity <= 0 || !countable(fulfillmentClient, merchantId, productCode)) {
            return null;
        }
        try {
            return fulfillmentClient.consume(merchantId, productCode, quantity);
        } catch (FulfillmentQuotaExceededException exception) {
            throw quotaExceeded(productCode);
        }
    }

    private ExternalProductAccessResponse readAccess(Long merchantId, String productCode) {
        return fulfillmentClient.findProductAccess(merchantId, productCode);
    }

    private boolean countable(FulfillmentServiceClient client, Long merchantId, String productCode) {
        return client.listEntitlements(merchantId).stream()
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
