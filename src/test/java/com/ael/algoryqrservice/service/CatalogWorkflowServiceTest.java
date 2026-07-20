package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.ProductResponse;
import com.ael.algoryqrservice.model.dto.SellablePackageComposeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogWorkflowServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private PlanPackageService planPackageService;

    @InjectMocks
    private CatalogWorkflowService catalogWorkflowService;

    @Test
    void createSellablePackage_whenNewProducts_thenCreateProductsAndPurchasablePackage() {
        SellablePackageComposeRequest request = new SellablePackageComposeRequest();
        request.setPackageName("Dijital Menu Pro");
        request.setValidityDays(30);
        request.setPriority(10);

        SellablePackageComposeRequest.ComposeItemRequest item = new SellablePackageComposeRequest.ComposeItemRequest();
        item.setProductName("QR Olusturma");
        item.setCountable(true);
        item.setQuantity(50);
        item.setUnitPrice(new BigDecimal("5.00"));
        item.setVatRate(new BigDecimal("20.00"));
        request.setItems(List.of(item));

        when(productService.create(any())).thenReturn(ProductResponse.builder()
                .id(7L)
                .code("QR_OLUSTURMA")
                .unitPrice(new BigDecimal("5.00"))
                .build());
        when(planPackageService.create(any())).thenReturn(PlanPackageResponse.builder()
                .id(3L)
                .name("Dijital Menu Pro")
                .subtotal(new BigDecimal("250.00"))
                .vatAmount(new BigDecimal("50.00"))
                .price(new BigDecimal("300.00"))
                .purchasable(true)
                .active(true)
                .build());

        PlanPackageResponse response = catalogWorkflowService.createSellablePackage(request);

        ArgumentCaptor<PlanPackageRequest> packageCaptor = ArgumentCaptor.forClass(PlanPackageRequest.class);
        verify(planPackageService).create(packageCaptor.capture());
        PlanPackageRequest saved = packageCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Dijital Menu Pro");
        assertThat(saved.resolvedPurchasable()).isTrue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getProductId()).isEqualTo(7L);
        assertThat(response.getPrice()).isEqualByComparingTo("300.00");
    }
}
