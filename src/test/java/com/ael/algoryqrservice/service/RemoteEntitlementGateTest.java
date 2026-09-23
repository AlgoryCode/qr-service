package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteEntitlementGateTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private RemoteEntitlementGate gate;

    @Mock
    private FulfillmentServiceClient client;

    @Test
    void hasScope_whenScopeMissingButProductGrantsIt_thenTrue() {
        when(client.listEntitlements(7L)).thenReturn(List.of(entitlement("QR_MENU")));
        when(productRepository.findByCode("QR_MENU")).thenReturn(Optional.of(menuProduct()));

        assertThat(gate.hasScope(client, 7L, "QR_MENU_OWNER")).isTrue();
    }

    @Test
    void hasScope_whenProductScopeDoesNotMatch_thenFalse() {
        when(client.listEntitlements(7L)).thenReturn(List.of(entitlement("QR_CREATE")));
        when(productRepository.findByCode("QR_CREATE")).thenReturn(Optional.of(
                Product.builder().code("QR_CREATE").name("QR").scopeCode("QR_CREATE_OWNER").build()
        ));

        assertThat(gate.hasScope(client, 7L, "QR_MENU_OWNER")).isFalse();
    }

    private Product menuProduct() {
        return Product.builder().code("QR_MENU").name("Menü").scopeCode("QR_MENU_OWNER").build();
    }

    private ExternalEntitlementResponse entitlement(String featureCode) {
        return new ExternalEntitlementResponse(
                9L,
                7L,
                2L,
                null,
                5L,
                "PACKAGE_PRODUCT",
                featureCode,
                null,
                1,
                true,
                0,
                "PACKAGE_INCLUDE",
                "ACTIVE",
                null,
                null,
                featureCode
        );
    }
}
