package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.FulfillmentGateMode;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentGateServiceTest {

    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private FulfillmentUsageLogRepository usageLogRepository;
    @Mock
    private UserEntitlementRepository userEntitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductRepository productRepository;

    private AppProperties appProperties;
    private FulfillmentGateService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getFulfillment().setGateMode(FulfillmentGateMode.FULFILLMENT_ONLY);
        service = new FulfillmentGateService(
                grantFulfillmentRepository,
                fulfillmentDetailRepository,
                usageLogRepository,
                userEntitlementRepository,
                purchaseRepository,
                productRepository,
                appProperties
        );
    }

    @Test
    void hasScope_whenFulfillmentDetailHasScope_thenAllow() {
        when(fulfillmentDetailRepository.existsActiveByScopeCode(
                eq(22L), eq(CatalogScopes.QR_CREATE_OWNER), any(LocalDateTime.class)
        )).thenReturn(true);

        assertThat(service.hasScope(22L, CatalogScopes.QR_CREATE_OWNER)).isTrue();
    }

    @Test
    void hasScope_whenFulfillmentDetailMissingScope_thenDeny() {
        when(fulfillmentDetailRepository.existsActiveByScopeCode(
                eq(22L), eq(CatalogScopes.QR_CREATE_OWNER), any(LocalDateTime.class)
        )).thenReturn(false);

        assertThat(service.hasScope(22L, CatalogScopes.QR_CREATE_OWNER)).isFalse();
    }

    @Test
    void consumeFeature_whenDetailHasRemaining_thenWriteUsageLog() {
        FulfillmentDetail detail = FulfillmentDetail.builder()
                .id(21L)
                .fulfillmentId(11L)
                .userId(22L)
                .featureCode(CatalogProducts.QR_CREATE)
                .quantity(5)
                .usedQuantity(1)
                .unlimited(false)
                .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                .build();
        when(fulfillmentDetailRepository.findAndLockActiveByFeatureCodeAndSource(
                eq(22L), eq(CatalogProducts.QR_CREATE), eq(FulfillmentDetailSource.PACKAGE_INCLUDE), any(LocalDateTime.class)
        )).thenReturn(List.of(detail));
        when(fulfillmentDetailRepository.findAndLockActiveByFeatureCodeAndSource(
                eq(22L), eq(CatalogProducts.QR_CREATE), eq(FulfillmentDetailSource.ADDON_PURCHASE), any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(grantFulfillmentRepository.findById(11L)).thenReturn(Optional.of(
                GrantFulfillment.builder().id(11L).purchaseId(333L).userId(22L).packageId(4L).build()
        ));

        var result = service.consumeFeature(22L, CatalogProducts.QR_CREATE, 1, FulfillmentReferenceType.QR, 34L);

        assertThat(result.fullyConsumed(1)).isTrue();
        assertThat(result.purchaseId()).isEqualTo(333L);
        assertThat(detail.getUsedQuantity()).isEqualTo(2);
        ArgumentCaptor<FulfillmentUsageLog> captor = ArgumentCaptor.forClass(FulfillmentUsageLog.class);
        verify(usageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetailId()).isEqualTo(21L);
        assertThat(captor.getValue().getAction()).isEqualTo(FulfillmentUsageAction.CONSUME);
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
        assertThat(captor.getValue().getReferenceType()).isEqualTo(FulfillmentReferenceType.QR);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(34L);
    }
}
