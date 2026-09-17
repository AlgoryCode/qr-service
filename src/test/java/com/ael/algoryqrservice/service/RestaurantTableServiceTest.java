package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.QrCodeGeneratorUtil;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantTableServiceTest {

    @Mock
    RestaurantTableRepository restaurantTableRepository;
    @Mock
    RestaurantAreaRepository restaurantAreaRepository;
    @Mock
    MenuRepository menuRepository;
    @Mock
    MenuService menuService;
    @Mock
    QrCodeGeneratorUtil qrCodeGeneratorUtil;
    @Mock
    SecurityUtils securityUtils;

    @InjectMocks
    RestaurantTableService restaurantTableService;

    @BeforeEach
    void owner() {
        Menu menu = new Menu();
        menu.setMenuId(8L);
        menu.setUserId(3L);
        menu.setDeleted(false);
        when(menuRepository.findById(8L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUserId()).thenReturn(3L);
        lenient().when(menuService.buildPublicUrl(any())).thenReturn("https://example.com/menu");
    }

    @Test
    void updateTable_savesFloorPlanCoordinates() {
        RestaurantTable table = RestaurantTable.builder()
                .id(11L)
                .menuId(8L)
                .name("Masa 1")
                .publicToken("tok")
                .active(true)
                .build();
        when(restaurantTableRepository.findByIdAndMenuId(11L, 8L)).thenReturn(Optional.of(table));
        when(restaurantTableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        restaurantTableService.updateTable(
                8L,
                11L,
                RestaurantTableDtos.UpdateTableRequest.builder()
                        .layoutX(120d)
                        .layoutY(-4d)
                        .layoutRotation(450)
                        .layoutShape("round")
                        .capacity(4)
                        .build()
        );

        ArgumentCaptor<RestaurantTable> captor = ArgumentCaptor.forClass(RestaurantTable.class);
        verify(restaurantTableRepository).save(captor.capture());
        assertThat(captor.getValue().getLayoutX()).isEqualTo(100d);
        assertThat(captor.getValue().getLayoutY()).isEqualTo(0d);
        assertThat(captor.getValue().getLayoutRotation()).isEqualTo(90);
        assertThat(captor.getValue().getLayoutShape()).isEqualTo("ROUND");
        assertThat(captor.getValue().getCapacity()).isEqualTo(4);
    }

    @Test
    void deleteTable_marksDeletedWithoutDeactivating() {
        RestaurantTable table = RestaurantTable.builder()
                .id(11L)
                .menuId(8L)
                .name("Masa 1")
                .publicToken("tok")
                .active(true)
                .deleted(false)
                .build();
        when(restaurantTableRepository.findByIdAndMenuId(11L, 8L)).thenReturn(Optional.of(table));
        when(restaurantTableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        restaurantTableService.deleteTable(8L, 11L);

        ArgumentCaptor<RestaurantTable> captor = ArgumentCaptor.forClass(RestaurantTable.class);
        verify(restaurantTableRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().isActive()).isTrue();
    }
}
