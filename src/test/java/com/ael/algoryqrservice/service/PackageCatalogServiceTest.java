package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageCatalogServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PackageCatalogService packageCatalogService;

    @Test
    void ensureCatalogProducts_whenMissing_thenCreateProducts() {
        when(productRepository.findByCode(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        packageCatalogService.ensureCatalogProducts();

        verify(productRepository).save(org.mockito.ArgumentMatchers.argThat(product ->
                CatalogProducts.QR_MENU.equals(product.getCode())
                        && CatalogScopes.QR_MENU_OWNER.equals(product.getScopeCode())
        ));
    }

    @Test
    void ensureCatalogProducts_whenExists_thenDoNotOverwrite() {
        Product existing = Product.builder()
                .id(1L)
                .code(CatalogProducts.QR_CREATE)
                .name("Existing")
                .scopeCode(CatalogScopes.QR_CREATE_OWNER)
                .active(true)
                .build();
        when(productRepository.findByCode(any())).thenReturn(Optional.of(existing));

        packageCatalogService.ensureCatalogProducts();

        verify(productRepository, never()).save(any());
    }
}
