package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    private com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService purchaseExpiryService;

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
        Product product = Product.builder()
                .id(9L)
                .code(CatalogProducts.SMART_ASSISTANT)
                .name("Smart Assistant")
                .active(true)
                .addonPurchasable(false)
                .build();
        when(productRepository.findByCode(CatalogProducts.SMART_ASSISTANT)).thenReturn(Optional.of(product));

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
                .addonPurchasable(true)
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
                .addonPurchasable(true)
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

    @Test
    void purchase_whenHostHasYearlyBilling_thenPersistAddonWithInheritedBillingPeriod() {
        AddonPurchaseRequest request = new AddonPurchaseRequest();
        request.setProductCode(CatalogProducts.QR_MENU);
        request.setBillingAddressId(1L);
        Product product = Product.builder()
                .id(2L)
                .code(CatalogProducts.QR_MENU)
                .name("QR Menu")
                .unitPrice(new BigDecimal("200.00"))
                .vatRate(new BigDecimal("20.00"))
                .consumable(true)
                .addonPurchasable(true)
                .active(true)
                .build();
        Purchase host = Purchase.builder()
                .id(3L)
                .userId(9L)
                .packageId(22L)
                .packageCode("PRO_PACKAGE")
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .billingPeriod(BillingPeriod.YEARLY)
                .billingIntervalMonths(12)
                .startsAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .expiresAt(LocalDateTime.of(2027, 8, 24, 0, 0))
                .build();
        BillingSnapshot billingSnapshot = BillingSnapshot.builder()
                .billingAddressId(1L)
                .type(BillingAddressType.INDIVIDUAL)
                .name("Tarik")
                .surname("Hamarat")
                .country("TR")
                .city("Istanbul")
                .phone("5551112233")
                .build();
        PackagePricingService.LinePrice line = new PackagePricingService.LinePrice(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnitPrice(),
                product.getVatRate(),
                1,
                new BigDecimal("200.00"),
                new BigDecimal("40.00"),
                new BigDecimal("240.00")
        );
        PaymentCheckoutFormRequest checkoutFormRequest = PaymentCheckoutFormRequest.builder().build();
        PaymentCheckoutFormResponse checkoutFormResponse = new PaymentCheckoutFormResponse();
        checkoutFormResponse.setConversationId("conv-addon-1");
        checkoutFormResponse.setPaymentPageUrl("https://paytr.com/pay");

        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.of(product));
        when(purchaseRepository.findByUserIdAndStatus(9L, PurchaseStatus.ACTIVE)).thenReturn(List.of(host));
        when(billingAddressService.resolveSnapshot(9L, 1L, null)).thenReturn(billingSnapshot);
        when(packagePricingService.calculateProduct(product, 1)).thenReturn(line);
        when(paymentRequestMapper.newPaymentAttemptId(9L)).thenReturn("conv-addon-1");
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId(340L);
            }
            return purchase;
        });
        when(appProperties.getServiceName()).thenReturn("qr-service");
        when(paymentRequestMapper.toAddonCheckoutFormRequest(
                any(Purchase.class),
                any(User.class),
                eq(CatalogProducts.QR_MENU),
                eq("QR Menu"),
                eq("127.0.0.1"),
                eq(appProperties),
                eq(paymentClientProperties),
                eq("conv-addon-1"),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(checkoutFormRequest);
        when(paymentServiceClient.initializeCheckoutForm(9L, checkoutFormRequest)).thenReturn(checkoutFormResponse);

        PurchaseInitiateResponse response = addonPurchaseService.purchase(user(), request, "127.0.0.1");

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository, atLeastOnce()).save(captor.capture());
        Purchase saved = captor.getAllValues().getFirst();
        assertThat(saved.getBillingPeriod()).isEqualTo(BillingPeriod.YEARLY);
        assertThat(saved.getBillingIntervalMonths()).isEqualTo(12);
        assertThat(saved.getPurchaseType()).isEqualTo(PurchaseType.ADD_ON);
        assertThat(response.getPurchaseId()).isEqualTo(340L);
        assertThat(response.getPaymentPageUrl()).isEqualTo("https://paytr.com/pay");
    }

    private User user() {
        User user = new User();
        user.setId(9L);
        user.setEmail("trkhamarat@gmail.com");
        return user;
    }
}
