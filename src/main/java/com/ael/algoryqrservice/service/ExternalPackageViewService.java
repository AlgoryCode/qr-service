package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.client.ActivePackageLookup;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExternalPackageViewService {

    private static final String ACTIVE = "ACTIVE";

    private final ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
    private final ExternalPackageResponseMapper mapper;

    public ExternalPackageViewService(
            ObjectProvider<FulfillmentServiceClient> fulfillmentClients,
            ExternalPackageResponseMapper mapper
    ) {
        this.fulfillmentClients = fulfillmentClients;
        this.mapper = mapper;
    }

    public Optional<List<PurchaseResponse>> purchases(Long userId) {
        Optional<FulfillmentServiceClient> client = client();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(active(client.get(), userId).map(mapper::toPurchase).map(List::of).orElseGet(List::of));
    }

    public Optional<List<UserEntitlementResponse>> entitlements(Long userId) {
        Optional<FulfillmentServiceClient> client = client();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        LoadedPackage loaded = load(client.get(), userId);
        return Optional.of(mapper.toEntitlements(loaded.entitlements(), loaded.purchaseId()));
    }

    public Optional<AccessSession> session(Long userId) {
        Optional<FulfillmentServiceClient> client = client();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(active(client.get(), userId)
                .map(mapper::toAllowSession)
                .orElseGet(() -> AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null)));
    }

    public Optional<SubscriptionOverviewResponse> overview(Long userId) {
        Optional<FulfillmentServiceClient> client = client();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        LoadedPackage loaded = load(client.get(), userId);
        List<UserEntitlementResponse> entitlements = mapper.toEntitlements(loaded.entitlements(), loaded.purchaseId());
        return Optional.of(SubscriptionOverviewResponse.builder()
                .activePackage(loaded.active().map(mapper::toSummary).orElse(null))
                .entitlements(entitlements)
                .addonPurchases(List.of())
                .fulfillmentDetails(mapper.toDetails(loaded.entitlements()))
                .fulfillmentActive(loaded.active().isPresent())
                .build());
    }

    private LoadedPackage load(FulfillmentServiceClient client, Long userId) {
        Optional<ExternalActivePackageResponse> active = active(client, userId);
        List<ExternalEntitlementResponse> entitlements = client.listEntitlements(userId);
        Long purchaseId = active.map(mapper::purchaseKey).orElse(null);
        return new LoadedPackage(active, entitlements, purchaseId);
    }

    private Optional<ExternalActivePackageResponse> active(FulfillmentServiceClient client, Long userId) {
        ActivePackageLookup lookup = client.lookupActivePackage(userId);
        if (!(lookup instanceof ActivePackageLookup.Found found)) {
            return Optional.empty();
        }
        if (!ACTIVE.equals(found.value().status())) {
            return Optional.empty();
        }
        return Optional.of(found.value());
    }

    private Optional<FulfillmentServiceClient> client() {
        return Optional.ofNullable(fulfillmentClients.getIfAvailable());
    }

    private record LoadedPackage(
            Optional<ExternalActivePackageResponse> active,
            List<ExternalEntitlementResponse> entitlements,
            Long purchaseId
    ) {
    }
}
