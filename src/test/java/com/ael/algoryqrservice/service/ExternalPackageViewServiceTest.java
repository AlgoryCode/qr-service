package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.ActivePackageLookup;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalPackageViewServiceTest {

    @Test
    void purchases_whenActivePackage_thenMapPurchaseAndEntitlement() {
        FulfillmentServiceClient client = mock(FulfillmentServiceClient.class);
        when(client.lookupActivePackage(7L)).thenReturn(new ActivePackageLookup.Found(activePackage()));
        when(client.listEntitlements(7L)).thenReturn(List.of(entitlement()));
        ExternalPackageViewService service = new ExternalPackageViewService(provider(client), new ExternalPackageResponseMapper());

        assertThat(service.purchases(7L)).hasValueSatisfying(purchases ->
                assertThat(purchases).singleElement().satisfies(purchase -> {
                    assertThat(purchase.getId()).isEqualTo(3L);
                    assertThat(purchase.getPackageCode()).isEqualTo("ULTIMATE_PACKAGE");
                    assertThat(purchase.getPurchaseType()).isEqualTo(PurchaseType.PAID);
                    assertThat(purchase.isUsable()).isTrue();
                })
        );
        assertThat(service.entitlements(7L)).hasValueSatisfying(entitlements ->
                assertThat(entitlements).singleElement().satisfies(entitlement -> {
                    assertThat(entitlement.getProductCode()).isEqualTo("QR_CREATE");
                    assertThat(entitlement.getPurchaseId()).isEqualTo(3L);
                    assertThat(entitlement.getRemainingQuantity()).isEqualTo(3);
                    assertThat(entitlement.isUsable()).isTrue();
                })
        );
        assertThat(service.session(7L)).get().satisfies(session -> {
            assertThat(session.decision()).isEqualTo(AccessDecision.ALLOW);
            assertThat(session.packageCode()).isEqualTo("ULTIMATE_PACKAGE");
        });
    }

    @Test
    void purchases_whenClientMissing_thenEmpty() {
        ExternalPackageViewService service = new ExternalPackageViewService(provider(null), new ExternalPackageResponseMapper());

        assertThat(service.purchases(7L)).isEmpty();
        assertThat(service.session(7L)).isEmpty();
    }

    @Test
    void session_whenPackageAbsent_thenRequirePurchase() {
        FulfillmentServiceClient client = mock(FulfillmentServiceClient.class);
        when(client.lookupActivePackage(7L)).thenReturn(new ActivePackageLookup.Absent());
        ExternalPackageViewService service = new ExternalPackageViewService(provider(client), new ExternalPackageResponseMapper());

        assertThat(service.session(7L)).get().satisfies(session ->
                assertThat(session.decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE)
        );
        assertThat(service.purchases(7L)).hasValueSatisfying(purchases -> assertThat(purchases).isEmpty());
    }

    private ExternalActivePackageResponse activePackage() {
        return new ExternalActivePackageResponse(
                1L,
                7L,
                2L,
                3L,
                4L,
                "ULTIMATE_PACKAGE",
                "Ultimate",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30),
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of("QR_CREATE"),
                List.of("QR_CREATE_OWNER")
        );
    }

    private ExternalEntitlementResponse entitlement() {
        return new ExternalEntitlementResponse(
                9L,
                7L,
                2L,
                null,
                5L,
                "PACKAGE_PRODUCT",
                "QR_CREATE",
                "QR_CREATE_OWNER",
                5,
                false,
                2,
                "PACKAGE_INCLUDE",
                "ACTIVE",
                null,
                null,
                "QR_CREATE"
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FulfillmentServiceClient> provider(FulfillmentServiceClient client) {
        ObjectProvider<FulfillmentServiceClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }
}
