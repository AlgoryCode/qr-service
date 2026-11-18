package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.billing.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureLabelsEntitlementNameTest {

    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final FeatureLabels labels = new FeatureLabels(featureRepository);

    @Test
    void replacesCodesWithCatalogNameAndDescription() {
        when(featureRepository.findAll()).thenReturn(List.of(
                feature("WAITER_PANEL", "Garson paneli", "Masadan sipariş alır."),
                feature("QR_BRANCH", "Şube", "  ")));

        List<UserEntitlementResponse> result = labels.name(List.of(
                entitlement("WAITER_PANEL"),
                entitlement("QR_BRANCH"),
                entitlement("UNKNOWN_CODE")));

        assertThat(result.get(0).getProductName()).isEqualTo("Garson paneli");
        assertThat(result.get(0).getProductDescription()).isEqualTo("Masadan sipariş alır.");
        assertThat(result.get(1).getProductName()).isEqualTo("Şube");
        assertThat(result.get(1).getProductDescription()).isNull();
        assertThat(result.get(2).getProductName()).isEqualTo("UNKNOWN_CODE");
        assertThat(result.get(2).getProductDescription()).isNull();
    }

    private static Feature feature(String key, String name, String description) {
        return Feature.builder().key(key).name(name).description(description).kind(Feature.Kind.BOOLEAN).build();
    }

    private static UserEntitlementResponse entitlement(String code) {
        return UserEntitlementResponse.builder().productCode(code).productName(code).build();
    }
}
