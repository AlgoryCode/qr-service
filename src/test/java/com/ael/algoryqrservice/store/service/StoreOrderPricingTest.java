package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductOption;
import com.ael.algoryqrservice.model.MenuProductOptionGroup;
import com.ael.algoryqrservice.service.MenuProductOptionService;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StorePaymentMethod;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreOrderPricingTest {

    @Mock
    private StoreCatalogService storeCatalogService;

    @Test
    void price_whenRequiredOptionMissing_thenThrow() {
        MenuProductOptionService optionService = new MenuProductOptionService(null) {
            @Override
            public Map<Long, List<MenuProductOptionGroup>> loadEntitiesByProductIds(
                    Collection<Long> productIds
            ) {
                return Map.of(1L, List.of(choiceGroup()));
            }
        };
        StoreOrderPricing pricing = new StoreOrderPricing(storeCatalogService, optionService);
        Merchant merchant = Merchant.builder()
                .catalogMenuId(9L)
                .minOrderAmount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .currency("TRY")
                .build();
        MenuProduct product = MenuProduct.builder()
                .productId(1L)
                .name("Yarım Tavuk")
                .price(new BigDecimal("150"))
                .available(true)
                .build();
        when(storeCatalogService.loadAvailableProducts(eq(9L), any())).thenReturn(Map.of(1L, product));

        StorePublicDtos.CreateOrderRequest request = new StorePublicDtos.CreateOrderRequest(
                "Ali",
                "555",
                StoreDeliveryType.PICKUP,
                StorePaymentMethod.CASH_ON_DELIVERY,
                null,
                null,
                List.of(new StorePublicDtos.OrderItemRequest(1L, 1, null, List.of()))
        );

        assertThatThrownBy(() -> pricing.price(merchant, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Seçim");
    }

    private static MenuProductOptionGroup choiceGroup() {
        MenuProductOptionGroup group = MenuProductOptionGroup.builder()
                .id(8L)
                .productId(1L)
                .name("Seçim")
                .minSelect(1)
                .maxSelect(1)
                .sortOrder(0)
                .build();
        group.addOption(MenuProductOption.builder()
                .id(3L)
                .name("Acılı")
                .priceDelta(BigDecimal.ZERO)
                .available(true)
                .sortOrder(0)
                .build());
        return group;
    }
}
