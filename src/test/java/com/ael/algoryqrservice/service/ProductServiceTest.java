package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogCodeFactory;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ProductRequest;
import com.ael.algoryqrservice.repository.PlanPackageItemRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PlanPackageItemRepository planPackageItemRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private UserEntitlementRepository userEntitlementRepository;
    @Mock
    private PackagePricingService packagePricingService;

    @Spy
    private CatalogCodeFactory catalogCodeFactory = new CatalogCodeFactory();

    @InjectMocks
    private ProductService productService;

    @Test
    void create_whenPriceGiven_thenPersistUnitPriceAndVat() {
        ProductRequest request = new ProductRequest();
        request.setName("QR Olusturma");
        request.setUnitPrice(new BigDecimal("100.00"));
        request.setVatRate(new BigDecimal("20.00"));
        when(productRepository.existsByCode("QR_OLUSTURMA")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });

        var response = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getVatRate()).isEqualByComparingTo("20.00");
        assertThat(response.getUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void delete_whenEntitlementExists_thenReject() {
        Product product = Product.builder().id(5L).code("TEMP").name("Temp").active(true).build();
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(userEntitlementRepository.existsByProductId(5L)).thenReturn(true);

        assertThatThrownBy(() -> productService.delete(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("haklarinda");

        verify(productRepository, never()).delete(any());
    }
}
