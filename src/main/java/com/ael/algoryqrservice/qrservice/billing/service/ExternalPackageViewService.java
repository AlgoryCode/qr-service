package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.access.AccessSession;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import com.ael.algoryqrservice.qrservice.billing.externalpackageresponsemapper.service.ExternalPackageResponseMapper;

@Service
@RequiredArgsConstructor
public class ExternalPackageViewService {

    private final FulfillmentServiceClient fulfillmentClient;
    private final ExternalPackageResponseMapper mapper;

    public Optional<List<PurchaseResponse>> purchases(Long merchantId) {
        return Optional.of(active(merchantId).map(mapper::toPurchase).map(List::of).orElseGet(List::of));
    }

    public Optional<List<UserEntitlementResponse>> entitlements(Long merchantId) {
        LoadedPackage loaded = load(merchantId);
        return Optional.of(mapper.toEntitlements(loaded.entitlements(), loaded.purchaseId()));
    }

    public Optional<AccessSession> session(Long merchantId) {
        return Optional.of(active(merchantId)
                .map(mapper::toAllowSession)
                .orElseGet(() -> AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null)));
    }

    public Optional<SubscriptionOverviewResponse> overview(Long merchantId) {
        LoadedPackage loaded = load(merchantId);
        List<UserEntitlementResponse> entitlements = mapper.toEntitlements(loaded.entitlements(), loaded.purchaseId());
        return Optional.of(SubscriptionOverviewResponse.builder()
                .activePackage(loaded.active().map(mapper::toSummary).orElse(null))
                .entitlements(entitlements)
                .addonPurchases(List.of())
                .fulfillmentDetails(mapper.toDetails(loaded.entitlements()))
                .fulfillmentActive(loaded.active().isPresent())
                .build());
    }

    private LoadedPackage load(Long merchantId) {
        Optional<ExternalActivePackageResponse> active = active(merchantId);
        List<ExternalEntitlementResponse> entitlements = fulfillmentClient.listEntitlements(merchantId);
        Long purchaseId = active.map(mapper::purchaseKey).orElse(null);
        return new LoadedPackage(active, entitlements, purchaseId);
    }

    private Optional<ExternalActivePackageResponse> active(Long merchantId) {
        PackageView view = fulfillmentClient.findPackage(merchantId);
        if (!"ACTIVE".equals(view.status()) || view.body() == null) {
            return Optional.empty();
        }
        return Optional.of(view.body());
    }

    private record LoadedPackage(
            Optional<ExternalActivePackageResponse> active,
            List<ExternalEntitlementResponse> entitlements,
            Long purchaseId
    ) {
    }
}
