package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddonPurchaseServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private BillingAddressService billingAddressService;
    @Mock
    private PackagePricingService packagePricingService;
    @Mock
    private PaymentRequestMapper paymentRequestMapper;
    @Mock
    private com.ael.algoryqrservice.client.PaymentServiceClient paymentServiceClient;
    @Mock
    private PurchaseFulfillmentService purchaseFulfillmentService;
    @Mock
    private com.ael.algoryqrservice.config.AppProperties appProperties;
    @Mock
    private com.ael.algoryqrservice.config.PaymentClientProperties paymentClientProperties;
    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private AddonPurchaseService addonPurchaseService;

    @BeforeEach
    void freezeClock() {
        AppTime.setClock(Clock.fixed(
                LocalDateTime.of(2026, 8, 24, 1, 0, 0).atZone(AppTime.ZONE).toInstant(),
                AppTime.ZONE
        ));
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void purchase_whenProductNotAddon_thenReject() {
        AddonPurchaseRequest request = new AddonPurchaseRequest();
        request.setProductCode(CatalogProducts.SMART_ASSISTANT);
        request.setBillingAddressId(1L);

        assertThatThrownBy(() -> addonPurchaseService.purchase(user(), request, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tekil");
    }

    @Test
    void purchase_whenNoHostPackage_thenReject() {
        AddonPurchaseRequest request = new AddonPurchaseRequest();
        request.setProductCode(CatalogProducts.QR_MENU);
        request.setBillingAddressId(1L);
        Product product = Product.builder()
                .id(2L)
                .code(CatalogProducts.QR_MENU)
                .name("QR Menu")
                .unitPrice(new BigDecimal("29.00"))
                .vatRate(new BigDecimal("20.00"))
                .consumable(true)
                .active(true)
                .build();
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(purchaseRepository.findByUserIdAndStatus(9L, PurchaseStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> addonPurchaseService.purchase(user(), request, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aktif bir paket");
    }

    @Test
    void purchase_whenHostIsOnlyAddon_thenReject() {
        AddonPurchaseRequest request = new AddonPurchaseRequest();
        request.setProductCode(CatalogProducts.QR_MENU);
        request.setBillingAddressId(1L);
        Product product = Product.builder()
                .id(2L)
                .code(CatalogProducts.QR_MENU)
                .name("QR Menu")
                .unitPrice(new BigDecimal("29.00"))
                .vatRate(new BigDecimal("20.00"))
                .consumable(true)
                .active(true)
                .build();
        Purchase addon = Purchase.builder()
                .id(5L)
                .userId(9L)
                .packageId(3L)
                .packageCode(CatalogProducts.QR_MENU)
                .purchaseType(PurchaseType.ADD_ON)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .expiresAt(LocalDateTime.of(2027, 8, 24, 0, 0))
                .build();
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(purchaseRepository.findByUserIdAndStatus(9L, PurchaseStatus.ACTIVE)).thenReturn(List.of(addon));

        assertThatThrownBy(() -> addonPurchaseService.purchase(user(), request, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aktif bir paket");
    }

    private User user() {
        User user = new User();
        user.setId(9L);
        user.setEmail("trkhamarat@gmail.com");
        return user;
    }
}
