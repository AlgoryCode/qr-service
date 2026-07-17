package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageCatalogServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;

    @InjectMocks
    private PackageCatalogService packageCatalogService;

    @Test
    void ensureFreePackage_whenMissing_thenCreateFreePackageOnce() {
        Product qrCreate = product(1L, CatalogProducts.QR_CREATE, CatalogScopes.QR_CREATE_OWNER, true);
        when(productRepository.findByCode(CatalogProducts.QR_CREATE)).thenReturn(Optional.of(qrCreate));
        when(productRepository.findByCode(CatalogProducts.QR_MENU)).thenReturn(Optional.empty());
        when(productRepository.findByCode(CatalogProducts.QR_AGENT)).thenReturn(Optional.empty());
        when(productRepository.findByCode(CatalogProducts.QR_ANALYTICS)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planPackageRepository.findByCode(CatalogPackages.FREE_PACKAGE)).thenReturn(Optional.empty());
        when(planPackageRepository.save(any(PlanPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanPackage result = packageCatalogService.ensureFreePackage();

        ArgumentCaptor<PlanPackage> captor = ArgumentCaptor.forClass(PlanPackage.class);
        verify(planPackageRepository).save(captor.capture());
        PlanPackage saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);
        assertThat(saved.isSystemManaged()).isTrue();
        assertThat(saved.isPurchasable()).isFalse();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getQuantity()).isEqualTo(5);
        assertThat(result.getCode()).isEqualTo(CatalogPackages.FREE_PACKAGE);
    }

    @Test
    void ensureFreePackage_whenExists_thenDoNotOverwrite() {
        PlanPackage existing = PlanPackage.builder()
                .id(1L)
                .code(CatalogPackages.FREE_PACKAGE)
                .name("Custom Free")
                .price(BigDecimal.ZERO)
                .systemManaged(true)
                .purchasable(false)
                .build();
        Product qrCreate = product(1L, CatalogProducts.QR_CREATE, CatalogScopes.QR_CREATE_OWNER, true);
        when(productRepository.findByCode(any())).thenReturn(Optional.of(qrCreate));
        when(planPackageRepository.findByCode(CatalogPackages.FREE_PACKAGE)).thenReturn(Optional.of(existing));

        PlanPackage result = packageCatalogService.ensureFreePackage();

        assertThat(result.getName()).isEqualTo("Custom Free");
        verify(planPackageRepository, org.mockito.Mockito.never()).save(any());
    }

    private Product product(Long id, String code, String scopeCode, boolean consumable) {
        return Product.builder()
                .id(id)
                .code(code)
                .name(code)
                .scopeCode(scopeCode)
                .consumable(consumable)
                .active(true)
                .build();
    }
}
