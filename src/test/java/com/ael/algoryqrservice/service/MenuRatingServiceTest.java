package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuRating;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuRatingRepository;
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
class MenuRatingServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuRatingRepository menuRatingRepository;

    @InjectMocks
    private MenuRatingService menuRatingService;

    @Test
    void rateMenu_whenValid_thenPersistAndUpdateAggregates() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .active(true)
                .publicAccessEnabled(true)
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0L)
                .build();

        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuRatingRepository.findByMenuIdAndIpAddress(10L, "1.2.3.4"))
                .thenReturn(Optional.empty());
        when(menuRatingRepository.save(any(MenuRating.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRatingRepository.averageScoreByMenuId(10L)).thenReturn(5.0);
        when(menuRatingRepository.countByMenuId(10L)).thenReturn(1L);
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.MenuRatingResponse response = menuRatingService.rateMenu(
                10L,
                MenuDtos.MenuRatingRequest.builder().score(5).comment("Güzel menü").build(),
                "1.2.3.4",
                "Mozilla/5.0"
        );

        assertThat(response.getScore()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Güzel menü");
        assertThat(response.getRatingAvg()).isEqualByComparingTo("5.00");
        assertThat(response.getRatingCount()).isEqualTo(1L);
        assertThat(response.getUserRating()).isEqualTo(5);

        ArgumentCaptor<MenuRating> ratingCaptor = ArgumentCaptor.forClass(MenuRating.class);
        verify(menuRatingRepository).save(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getScore()).isEqualTo((short) 5);
        assertThat(ratingCaptor.getValue().getIpAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void rateMenu_whenScoreOutOfRange_thenThrow() {
        assertThatThrownBy(() -> menuRatingService.rateMenu(
                10L,
                MenuDtos.MenuRatingRequest.builder().score(0).build(),
                "1.1.1.1",
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1 ile 5");
    }

    @Test
    void rateMenu_whenSameIpExists_thenUpdateExisting() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .active(true)
                .publicAccessEnabled(true)
                .ratingAvg(new BigDecimal("4.00"))
                .ratingCount(1L)
                .build();
        MenuRating existing = MenuRating.builder()
                .id(99L)
                .menuId(10L)
                .ipAddress("9.9.9.9")
                .score((short) 4)
                .build();

        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuRatingRepository.findByMenuIdAndIpAddress(10L, "9.9.9.9"))
                .thenReturn(Optional.of(existing));
        when(menuRatingRepository.save(any(MenuRating.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRatingRepository.averageScoreByMenuId(10L)).thenReturn(3.0);
        when(menuRatingRepository.countByMenuId(10L)).thenReturn(1L);
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.MenuRatingResponse response = menuRatingService.rateMenu(
                10L,
                MenuDtos.MenuRatingRequest.builder().score(3).build(),
                "9.9.9.9",
                "ua"
        );

        assertThat(response.getScore()).isEqualTo(3);
        assertThat(response.getRatingAvg()).isEqualByComparingTo("3.00");
        assertThat(existing.getScore()).isEqualTo((short) 3);
    }
}
