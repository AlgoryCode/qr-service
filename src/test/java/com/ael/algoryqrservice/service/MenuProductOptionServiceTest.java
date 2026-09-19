package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuProductOption;
import com.ael.algoryqrservice.model.MenuProductOptionGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ael.algoryqrservice.repository.MenuProductOptionGroupRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuProductOptionServiceTest {

    @Mock
    private MenuProductOptionGroupRepository optionGroupRepository;

    private MenuProductOptionService service;

    @BeforeEach
    void setUp() {
        service = new MenuProductOptionService(optionGroupRepository);
    }

    @Test
    void resolveSelections_whenRequiredMissing_thenThrow() {
        MenuProductOptionGroup group = sizeGroup();
        assertThatThrownBy(() -> service.resolveSelections(1L, List.of(group), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Boyut");
    }

    @Test
    void resolveSelections_whenValid_thenSumDeltas() {
        MenuProductOptionGroup group = sizeGroup();
        MenuProductOptionService.ResolvedSelections resolved =
                service.resolveSelections(1L, List.of(group), List.of(2L));
        assertThat(resolved.priceDeltaTotal()).isEqualByComparingTo("25.00");
        assertThat(resolved.selectedOptions()).hasSize(1);
        assertThat(resolved.selectedOptions().get(0).getOptionName()).isEqualTo("Büyük");
    }

    @Test
    void lineKey_ordersOptionIds() {
        assertThat(MenuProductOptionService.lineKey(10L, List.of(
                com.ael.algoryqrservice.model.SelectedMenuOption.builder().optionId(5L).build(),
                com.ael.algoryqrservice.model.SelectedMenuOption.builder().optionId(2L).build()
        ))).isEqualTo("10:2,5");
    }

    @Test
    void overwriteOnto_whenTargetsPresent_thenReplaceCopies() {
        MenuProductOptionGroup source = sizeGroup();
        source.setProductId(10L);
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(source));

        service.overwriteOnto(10L, List.of(10L, 20L));

        verify(optionGroupRepository).deleteByProductId(20L);
        ArgumentCaptor<List<MenuProductOptionGroup>> captor = ArgumentCaptor.captor();
        verify(optionGroupRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getProductId()).isEqualTo(20L);
        assertThat(captor.getValue().getFirst().getName()).isEqualTo("Boyut");
        assertThat(captor.getValue().getFirst().getOptions()).hasSize(2);
    }

    private static MenuProductOptionGroup sizeGroup() {
        MenuProductOptionGroup group = MenuProductOptionGroup.builder()
                .id(1L)
                .productId(1L)
                .name("Boyut")
                .minSelect(1)
                .maxSelect(1)
                .sortOrder(0)
                .build();
        group.addOption(MenuProductOption.builder()
                .id(1L)
                .name("Küçük")
                .priceDelta(BigDecimal.ZERO)
                .available(true)
                .sortOrder(0)
                .build());
        group.addOption(MenuProductOption.builder()
                .id(2L)
                .name("Büyük")
                .priceDelta(new BigDecimal("25.00"))
                .available(true)
                .sortOrder(1)
                .build());
        return group;
    }
}
