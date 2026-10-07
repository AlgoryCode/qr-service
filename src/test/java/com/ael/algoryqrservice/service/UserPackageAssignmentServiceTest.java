package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.client.dto.AssignedProduct;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ConflictException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPackageAssignmentServiceTest {

    @Mock
    ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
    @Mock
    FulfillmentServiceClient fulfillmentClient;
    @Mock
    AuthMerchantClient authMerchantClient;
    @Mock
    UserDebtService userDebtService;
    @Mock
    PlanPackageRepository planPackageRepository;

    UserPackageAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new UserPackageAssignmentService(
                fulfillmentClients,
                authMerchantClient,
                userDebtService,
                planPackageRepository,
                new PackagePricingService()
        );
    }

    @Test
    void create_whenCatalogMatches_thenWritesDatabaseCodeAndDays() {
        when(authMerchantClient.findMerchant(7L)).thenReturn(new AuthMerchantClient.MerchantView(7L, "ACTIVE"));
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
        when(fulfillmentClient.findPackage(7L)).thenReturn(new PackageView("INACTIVE", null));
        when(userDebtService.findDebt(7L)).thenReturn(Optional.empty());
        when(planPackageRepository.findByCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)).thenReturn(Optional.of(plan()));
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.of(plan()));

        service.create(7L, null, CatalogPackages.ULTIMATE_TRIAL_PACKAGE);

        verify(fulfillmentClient).createPackage(
                eq(7L),
                eq(CatalogPackages.ULTIMATE_TRIAL_PACKAGE),
                eq(15),
                eq(List.of(new AssignedProduct("QR_MENU", "Menu", 1, false))),
                eq(List.of())
        );
    }

    @Test
    void create_whenMerchantInactive_thenReject() {
        when(authMerchantClient.findMerchant(7L)).thenReturn(new AuthMerchantClient.MerchantView(7L, "INACTIVE"));

        assertThatThrownBy(() -> service.create(7L, 4L, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(error -> ((BadRequestException) error).getCode())
                .isEqualTo(UserPackageAssignmentService.ACCOUNT_INACTIVE);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
    }

    @Test
    void create_whenPackageActive_thenConflict() {
        when(authMerchantClient.findMerchant(7L)).thenReturn(new AuthMerchantClient.MerchantView(7L, "ACTIVE"));
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
        when(fulfillmentClient.findPackage(7L)).thenReturn(new PackageView("ACTIVE", null));

        assertThatThrownBy(() -> service.create(7L, 4L, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_whenPriceDiffers_thenReject() {
        PlanPackage plan = plan();
        plan.setCode("ULTIMATE_PACKAGE");
        plan.setPurchasable(true);
        plan.setSystemManaged(false);
        plan.setPrice(new BigDecimal("1.00"));
        when(authMerchantClient.findMerchant(7L)).thenReturn(new AuthMerchantClient.MerchantView(7L, "ACTIVE"));
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
        when(fulfillmentClient.findPackage(7L)).thenReturn(new PackageView("INACTIVE", null));
        when(userDebtService.findDebt(7L)).thenReturn(Optional.empty());
        when(planPackageRepository.findByIdWithItems(4L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.create(7L, 4L, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(error -> ((BadRequestException) error).getCode())
                .isEqualTo(UserPackageAssignmentService.PACKAGE_PRICE_MISMATCH);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
    }

    private PlanPackage plan() {
        Product product = Product.builder()
                .id(1L)
                .code("QR_MENU")
                .name("Menu")
                .unitPrice(new BigDecimal("100.00"))
                .vatRate(new BigDecimal("20.00"))
                .active(true)
                .build();
        PlanPackageItem item = PlanPackageItem.builder()
                .product(product)
                .quantity(1)
                .unlimited(false)
                .build();
        return PlanPackage.builder()
                .id(4L)
                .code(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .name("Deneme")
                .price(new BigDecimal("120.00"))
                .subtotal(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("20.00"))
                .currency("TRY")
                .active(true)
                .validityDays(15)
                .purchasable(false)
                .systemManaged(true)
                .items(List.of(item))
                .build();
    }
}
