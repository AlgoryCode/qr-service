package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantArea;
import com.ael.algoryqrservice.model.dto.RestaurantAreaDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantAreaServiceTest {

    @Mock
    RestaurantAreaRepository restaurantAreaRepository;
    @Mock
    RestaurantTableRepository restaurantTableRepository;
    @Mock
    MenuRepository menuRepository;
    @Mock
    SecurityUtils securityUtils;

    @InjectMocks
    RestaurantAreaService restaurantAreaService;

    @BeforeEach
    void owner() {
        Menu menu = new Menu();
        menu.setMenuId(8L);
        menu.setUserId(3L);
        menu.setDeleted(false);
        when(menuRepository.findById(8L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUserId()).thenReturn(3L);
    }

    @Test
    void createArea_assignsNextSortOrder() {
        when(restaurantAreaRepository.findByMenuIdOrderBySortOrderAscIdAsc(8L)).thenReturn(List.of(
                RestaurantArea.builder().id(1L).menuId(8L).name("İç Mekan").sortOrder(10).build()
        ));
        when(restaurantAreaRepository.save(any())).thenAnswer(invocation -> {
            RestaurantArea area = invocation.getArgument(0);
            area.setId(22L);
            return area;
        });

        RestaurantAreaDtos.AreaResponse response = restaurantAreaService.createArea(
                8L,
                RestaurantAreaDtos.CreateAreaRequest.builder().name(" Teras ").build()
        );

        ArgumentCaptor<RestaurantArea> captor = ArgumentCaptor.forClass(RestaurantArea.class);
        verify(restaurantAreaRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Teras");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(20);
        assertThat(response.getId()).isEqualTo(22L);
    }

    @Test
    void createArea_rejectsDuplicateName() {
        when(restaurantAreaRepository.findByMenuIdOrderBySortOrderAscIdAsc(8L)).thenReturn(List.of(
                RestaurantArea.builder().id(1L).menuId(8L).name("Teras").sortOrder(10).build()
        ));

        assertThatThrownBy(() -> restaurantAreaService.createArea(
                8L,
                RestaurantAreaDtos.CreateAreaRequest.builder().name(" teras ").build()
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("zaten kullanılıyor");
    }
}
