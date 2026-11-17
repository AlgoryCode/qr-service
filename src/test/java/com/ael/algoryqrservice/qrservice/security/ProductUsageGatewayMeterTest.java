package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.fulfillmentservice.application.metering.MeteringService;
import com.ael.algoryqrservice.qrservice.billing.service.UpgradeOptionService;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.exception.EntitlementDeniedException;
import com.ael.algoryqrservice.qrservice.exception.EntitlementDeniedException.Upgrade;
import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductUsageGatewayMeterTest {

    private final FulfillmentServiceClient client = mock(FulfillmentServiceClient.class);
    private final UpgradeOptionService upgrades = mock(UpgradeOptionService.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final FeatureRepository features = mock(FeatureRepository.class);
    private final ProductUsageGateway gateway = new ProductUsageGateway(client, upgrades, products, features);

    @Test
    void spendWithinAllowancePasses() {
        MeteringService.Charge ok = new MeteringService.Charge(true, false, usage(1, 5L));
        when(client.meter(1L, "ASSISTANT_QUESTION", 1, "k", null)).thenReturn(ok);

        assertThat(gateway.meter(1L, "ASSISTANT_QUESTION", 1, "k", null)).isSameAs(ok);
    }

    @Test
    void exhaustedAllowanceSuggestsWhatUnlocksTheParentFeature() {
        when(client.meter(1L, "ASSISTANT_QUESTION", 1, "k", null))
                .thenReturn(new MeteringService.Charge(false, false, usage(5, 5L)));
        when(client.findActivePackage(1L)).thenReturn(Optional.empty());
        when(products.findByCode(anyString())).thenReturn(Optional.empty());
        when(upgrades.forFeature(eq("ASSISTANT_QUESTION"), any(), any())).thenReturn(List.of());
        Upgrade ultimate = new Upgrade("PACKAGE", "ULTIMATE_PACKAGE", "Ultimate", null, true);
        when(upgrades.forFeature(eq("SMART_ASSISTANT"), any(), any())).thenReturn(List.of(ultimate));
        when(features.findById("ASSISTANT_QUESTION"))
                .thenReturn(Optional.of(Feature.builder().key("ASSISTANT_QUESTION").parentKey("SMART_ASSISTANT").build()));

        assertThatThrownBy(() -> gateway.meter(1L, "ASSISTANT_QUESTION", 1, "k", null))
                .isInstanceOfSatisfying(EntitlementDeniedException.class, denied -> {
                    assertThat(denied.getCode()).isEqualTo(EntitlementDeniedException.LIMIT_REACHED);
                    assertThat(denied.limit()).isEqualTo(5L);
                    assertThat(denied.used()).isEqualTo(5L);
                    assertThat(denied.upgrades()).containsExactly(ultimate);
                });
    }

    private static MeteringService.Usage usage(long used, Long limit) {
        return new MeteringService.Usage("ASSISTANT_QUESTION", "2026-11-17", used, limit, null);
    }
}
