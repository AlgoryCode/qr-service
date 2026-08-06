package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductRating;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductRatingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductRatingServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuProductRatingRepository menuProductRatingRepository;

    @InjectMocks
    private MenuProductRatingService menuProductRatingService;

    @Test
    void rateProduct_whenValid_thenPersistAndUpdateAggregates() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .active(true)
                .publicAccessEnabled(true)
                .build();
        MenuProduct product = MenuProduct.builder()
                .productId(5L)
                .menuId(10L)
                .name("Köfte")
                .available(true)
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0L)
                .build();

        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuProductRepository.findByProductIdAndDeletedFalse(5L)).thenReturn(Optional.of(product));
        when(menuProductRatingRepository.findByMenuProductIdAndIpAddress(5L, "1.2.3.4"))
                .thenReturn(Optional.empty());
        when(menuProductRatingRepository.save(any(MenuProductRating.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(menuProductRatingRepository.averageScoreByProductId(5L)).thenReturn(5.0);
        when(menuProductRatingRepository.countByMenuProductId(5L)).thenReturn(1L);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.ProductRatingRequest request = MenuDtos.ProductRatingRequest.builder()
                .score(5)
                .comment("Harika")
                .build();

        MenuDtos.ProductRatingResponse response = menuProductRatingService.rateProduct(
                10L, 5L, request, "1.2.3.4", "Mozilla/5.0"
        );

        assertThat(response.getScore()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Harika");
        assertThat(response.getRatingAvg()).isEqualByComparingTo("5.00");
        assertThat(response.getRatingCount()).isEqualTo(1L);

        ArgumentCaptor<MenuProductRating> ratingCaptor = ArgumentCaptor.forClass(MenuProductRating.class);
        verify(menuProductRatingRepository).save(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getScore()).isEqualTo((short) 5);
        assertThat(ratingCaptor.getValue().getIpAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void rateProduct_whenScoreOutOfRange_thenThrow() {
        MenuDtos.ProductRatingRequest request = MenuDtos.ProductRatingRequest.builder().score(6).build();

        assertThatThrownBy(() -> menuProductRatingService.rateProduct(10L, 5L, request, "1.1.1.1", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1 ile 5");
    }

    @Test
    void rateProduct_whenSameIpExists_thenUpdateExisting() {
        Menu menu = Menu.builder().menuId(10L).active(true).publicAccessEnabled(true).build();
        MenuProduct product = MenuProduct.builder()
                .productId(5L)
                .menuId(10L)
                .available(true)
                .ratingAvg(new BigDecimal("4.00"))
                .ratingCount(1L)
                .build();
        MenuProductRating existing = MenuProductRating.builder()
                .id(99L)
                .menuId(10L)
                .menuProductId(5L)
                .ipAddress("9.9.9.9")
                .score((short) 4)
                .build();

        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuProductRepository.findByProductIdAndDeletedFalse(5L)).thenReturn(Optional.of(product));
        when(menuProductRatingRepository.findByMenuProductIdAndIpAddress(5L, "9.9.9.9"))
                .thenReturn(Optional.of(existing));
        when(menuProductRatingRepository.save(any(MenuProductRating.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(menuProductRatingRepository.averageScoreByProductId(5L)).thenReturn(3.0);
        when(menuProductRatingRepository.countByMenuProductId(5L)).thenReturn(1L);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.ProductRatingResponse response = menuProductRatingService.rateProduct(
                10L,
                5L,
                MenuDtos.ProductRatingRequest.builder().score(3).build(),
                "9.9.9.9",
                "ua"
        );

        assertThat(response.getScore()).isEqualTo(3);
        assertThat(response.getRatingAvg()).isEqualByComparingTo("3.00");
        assertThat(existing.getScore()).isEqualTo((short) 3);
    }
}
