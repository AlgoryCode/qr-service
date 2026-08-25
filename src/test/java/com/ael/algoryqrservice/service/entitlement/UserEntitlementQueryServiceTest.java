package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEntitlementQueryServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private GrantFulfillmentRepository grantFulfillmentRepository;
    @Mock
    private FulfillmentUsageLogRepository usageLogRepository;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UserEntitlementQueryService entitlementQueryService;

    @Test
    void forUser_whenFulfillmentDetailsExist_thenMapRemainingQuantityFromUsage() {
        Purchase purchase = activePurchase();
        FulfillmentDetail detail = FulfillmentDetail.builder()
                .id(21L)
                .fulfillmentId(11L)
                .userId(USER_ID)
                .productId(3L)
                .featureCode(CatalogProducts.QR_CREATE)
                .quantity(100)
                .usedQuantity(3)
                .unlimited(false)
                .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build();
        GrantFulfillment grant = GrantFulfillment.builder()
                .id(11L)
                .userId(USER_ID)
                .purchaseId(10L)
                .packageId(4L)
                .build();

        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(USER_ID), any())).thenReturn(List.of(detail));
        when(grantFulfillmentRepository.findAllById(List.of(11L))).thenReturn(List.of(grant));
        when(purchaseRepository.findAllById(List.of(10L))).thenReturn(List.of(purchase));
        when(usageLogRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(productRepository.findAllById(List.of(3L))).thenReturn(List.of(
                Product.builder().id(3L).code(CatalogProducts.QR_CREATE).name("QR").build()
        ));

        List<UserEntitlementResponse> responses = entitlementQueryService.forUser(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getProductCode()).isEqualTo(CatalogProducts.QR_CREATE);
        assertThat(responses.getFirst().getProductName()).isEqualTo("QR");
        assertThat(responses.getFirst().getUsedQuantity()).isEqualTo(3);
        assertThat(responses.getFirst().getRemainingQuantity()).isEqualTo(97);
        assertThat(responses.getFirst().isUsable()).isTrue();
    }

    @Test
    void forPurchase_whenNoGrantExists_thenFallBackToLegacyEntitlements() {
        Purchase purchase = activePurchase();
        when(grantFulfillmentRepository.findByPurchaseId(10L)).thenReturn(java.util.Optional.empty());
        when(entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(10L)).thenReturn(List.of(
                com.ael.algoryqrservice.model.UserEntitlement.builder()
                        .id(1L)
                        .userId(USER_ID)
                        .productId(3L)
                        .productCode(CatalogProducts.QR_CREATE)
                        .purchaseId(10L)
                        .totalQuantity(10)
                        .remainingQuantity(7)
                        .usedQuantity(3)
                        .unlimited(false)
                        .startsAt(purchase.getStartsAt())
                        .expiresAt(purchase.getExpiresAt())
                        .build()
        ));
        when(productRepository.findAllById(List.of(3L))).thenReturn(List.of(
                Product.builder().id(3L).code(CatalogProducts.QR_CREATE).name("QR").build()
        ));

        List<UserEntitlementResponse> responses = entitlementQueryService.forPurchase(purchase);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getRemainingQuantity()).isEqualTo(7);
        assertThat(responses.getFirst().getProductName()).isEqualTo("QR");
    }

    private Purchase activePurchase() {
        return Purchase.builder()
                .id(10L)
                .userId(USER_ID)
                .packageId(4L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
