package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.fulfillmentservice.application.service.EntitlementUsageService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.PackageAlreadyAssignedException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.QuotaExceededException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ListedEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ActivePackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ConsumeEntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.EntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ProductAccessResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.mapper.FulfillmentPresentationMapper;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentServiceClient {

    private final FulfillmentService fulfillmentService;
    private final EntitlementUsageService entitlementUsageService;
    private final FulfillmentPresentationMapper mapper;

    public void createPackage(Long merchantId, String packageCode, int periodDays) {
        try {
            fulfillmentService.assignPackage(merchantId, packageCode, periodDays);
        } catch (PackageAlreadyAssignedException exception) {
            log.debug("Package already assigned for merchantId={}", merchantId);
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public PackageView findPackage(Long merchantId) {
        try {
            ActivePackageResponse response = mapper.toActivePackageResponse(
                    fulfillmentService.requireActivePackageView(merchantId)
            );
            ExternalActivePackageResponse external = toExternal(response);
            return new PackageView(statusOf(external), external);
        } catch (ResourceNotFoundException exception) {
            return new PackageView("INACTIVE", null);
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public Optional<ExternalActivePackageResponse> findActivePackage(Long merchantId) {
        try {
            PackageView view = findPackage(merchantId);
            if (!"ACTIVE".equals(view.status()) || view.body() == null) {
                return Optional.empty();
            }
            return Optional.of(view.body());
        } catch (FulfillmentUnavailableException exception) {
            return Optional.empty();
        }
    }

    public List<ExternalEntitlementResponse> listEntitlements(Long merchantId) {
        try {
            return entitlementUsageService.listActive(merchantId).stream()
                    .map(this::toExternal)
                    .toList();
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public ExternalProductAccessResponse findProductAccess(Long merchantId, String productCode) {
        try {
            ProductAccessResponse response = mapper.toProductAccess(
                    entitlementUsageService.productAccess(merchantId, productCode)
            );
            return new ExternalProductAccessResponse(
                    response.allowed(),
                    response.packageCode(),
                    response.featureCode(),
                    response.scopeCode()
            );
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public ExternalConsumeResponse consume(Long merchantId, String productCode, int quantity) {
        try {
            return toExternal(mapper.toConsume(entitlementUsageService.consume(merchantId, productCode, quantity)));
        } catch (QuotaExceededException exception) {
            throw new FulfillmentQuotaExceededException();
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public ExternalConsumeResponse release(Long merchantId, String productCode, int quantity) {
        try {
            return entitlementUsageService.release(merchantId, productCode, quantity)
                    .map(mapper::toConsume)
                    .map(this::toExternal)
                    .orElseGet(() -> toExternal(mapper.emptyConsume()));
        } catch (RuntimeException exception) {
            throw unavailable(merchantId, exception);
        }
    }

    public void requireAllowed(Long merchantId, String productCode) {
        if (!findProductAccess(merchantId, productCode).allowed()) {
            throw new ForbiddenException(productCode + " yetkisi için uygun paket gerekli");
        }
    }

    public ExternalConsumeResponse consumeRequired(Long merchantId, String productCode, int quantity) {
        try {
            return consume(merchantId, productCode, quantity);
        } catch (FulfillmentQuotaExceededException exception) {
            throw new ForbiddenException(
                    "Yetersiz veya süresi dolmuş " + productCode + " hakkı. Lütfen paket satın alın."
            );
        }
    }

    public int remaining(Long merchantId, String productCode) {
        return listEntitlements(merchantId).stream()
                .filter(item -> matches(item, productCode))
                .filter(this::open)
                .mapToInt(this::remainingOf)
                .max()
                .orElse(0);
    }

    public int openQuantity(Long merchantId, String productCode, String source) {
        return listEntitlements(merchantId).stream()
                .filter(item -> matches(item, productCode))
                .filter(item -> source == null || source.equals(item.source()))
                .filter(this::open)
                .mapToInt(this::granted)
                .sum();
    }

    private boolean matches(ExternalEntitlementResponse item, String productCode) {
        return productCode.equals(item.productCode()) || productCode.equals(item.featureCode());
    }

    private boolean open(ExternalEntitlementResponse item) {
        if (item.status() != null && !"ACTIVE".equals(item.status())) {
            return false;
        }
        return item.expiresAt() == null || item.expiresAt().isAfter(Instant.now());
    }

    private int remainingOf(ExternalEntitlementResponse item) {
        if (item.unlimited()) {
            return Integer.MAX_VALUE;
        }
        int total = item.quantity() == null ? 0 : item.quantity();
        int used = item.usedQuantity() == null ? 0 : item.usedQuantity();
        return Math.max(0, total - used);
    }

    private int granted(ExternalEntitlementResponse item) {
        if (item.unlimited()) {
            return Integer.MAX_VALUE;
        }
        return item.quantity() == null ? 0 : item.quantity();
    }

    private ExternalActivePackageResponse toExternal(ActivePackageResponse response) {
        return new ExternalActivePackageResponse(
                response.id(),
                response.merchantId(),
                response.fulfillmentId(),
                response.purchaseId(),
                response.packageId(),
                response.packageCode(),
                response.packageName(),
                response.periodStart(),
                response.periodEnd(),
                response.status() == null ? null : response.status().name(),
                response.activatedAt(),
                response.updatedAt(),
                response.products(),
                response.scopes()
        );
    }

    private ExternalEntitlementResponse toExternal(ListedEntitlement listed) {
        EntitlementResponse response = mapper.toEntitlementResponse(listed.entitlement(), listed.productCode());
        return new ExternalEntitlementResponse(
                response.id(),
                response.merchantId(),
                response.fulfillmentId(),
                response.purchaseId(),
                response.productId(),
                response.productTypeId() == null ? null : response.productTypeId().name(),
                response.featureCode(),
                response.scopeCode(),
                response.quantity(),
                response.unlimited(),
                response.usedQuantity(),
                response.source() == null ? null : response.source().name(),
                response.status() == null ? null : response.status().name(),
                response.startsAt(),
                response.expiresAt(),
                response.productCode()
        );
    }

    private ExternalConsumeResponse toExternal(ConsumeEntitlementResponse response) {
        return new ExternalConsumeResponse(
                response.entitlementId(),
                response.purchaseId(),
                response.usedQuantity(),
                response.remainingQuantity(),
                response.unlimited()
        );
    }

    private String statusOf(ExternalActivePackageResponse response) {
        if (response == null) {
            return "INACTIVE";
        }
        String status = response.status();
        if (!PackageStatus.ACTIVE.name().equals(status)) {
            return "INACTIVE";
        }
        LocalDate periodEnd = response.periodEnd();
        if (periodEnd != null && periodEnd.isBefore(LocalDate.now(AppTime.ZONE))) {
            return "INACTIVE";
        }
        return "ACTIVE";
    }

    private FulfillmentUnavailableException unavailable(Long merchantId, Exception exception) {
        log.warn(
                "In-process fulfillment call failed. userId={} reason={}",
                merchantId,
                exception.getMessage()
        );
        return new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
    }
}
