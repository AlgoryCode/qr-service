package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.StockIngredient;
import com.ael.algoryqrservice.model.StockMovement;
import com.ael.algoryqrservice.model.StockRecipeLine;
import com.ael.algoryqrservice.model.enums.StockMovementKind;
import com.ael.algoryqrservice.model.enums.StockSourceType;
import com.ael.algoryqrservice.model.enums.StockUnit;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.StockIngredientRepository;
import com.ael.algoryqrservice.repository.StockMovementRepository;
import com.ael.algoryqrservice.repository.StockRecipeLineRepository;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockConsumptionServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private StockRecipeLineRepository recipeLineRepository;
    @Mock
    private StockIngredientRepository ingredientRepository;
    @Mock
    private StockMovementRepository movementRepository;

    @InjectMocks
    private StockConsumptionService stockConsumptionService;

    @Test
    void consumeMenuOrder_whenRecipeCoversItems_thenDecreasesBalance() {
        MenuOrder order = menuOrder(2);
        StockIngredient ingredient = ingredient("10.000");
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu(9L)));
        when(recipeLineRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(recipe("1.500")));
        when(ingredientRepository.lockByIds(anyCollection())).thenReturn(List.of(ingredient));
        when(movementRepository.existsByIngredientIdAndKindAndSourceTypeAndSourceId(
                4L, StockMovementKind.SALE, StockSourceType.MENU_ORDER, 8L
        )).thenReturn(false);
        when(ingredientRepository.save(any(StockIngredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stockConsumptionService.consumeMenuOrder(order);

        assertThat(ingredient.getQuantity()).isEqualByComparingTo("7.000");
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(StockMovementKind.SALE);
        assertThat(captor.getValue().getQuantityDelta()).isEqualByComparingTo("-3.000");
        assertThat(captor.getValue().getSourceId()).isEqualTo(8L);
    }

    @Test
    void consumeMenuOrder_whenAlreadySold_thenDoesNotDecreaseAgain() {
        StockIngredient ingredient = ingredient("10.000");
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu(9L)));
        when(recipeLineRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(recipe("1.500")));
        when(ingredientRepository.lockByIds(anyCollection())).thenReturn(List.of(ingredient));
        when(movementRepository.existsByIngredientIdAndKindAndSourceTypeAndSourceId(
                4L, StockMovementKind.SALE, StockSourceType.MENU_ORDER, 8L
        )).thenReturn(true);

        stockConsumptionService.consumeMenuOrder(menuOrder(2));

        assertThat(ingredient.getQuantity()).isEqualByComparingTo("10.000");
        verify(movementRepository, never()).save(any());
    }

    @Test
    void consumeMenuOrder_whenStockInsufficient_thenThrowsConflict() {
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu(9L)));
        when(recipeLineRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(recipe("1.500")));
        when(ingredientRepository.lockByIds(anyCollection())).thenReturn(List.of(ingredient("1.000")));
        when(movementRepository.existsByIngredientIdAndKindAndSourceTypeAndSourceId(
                4L, StockMovementKind.SALE, StockSourceType.MENU_ORDER, 8L
        )).thenReturn(false);

        assertThatThrownBy(() -> stockConsumptionService.consumeMenuOrder(menuOrder(2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Yetersiz stok");
        verify(movementRepository, never()).save(any());
    }

    @Test
    void consumeMenuOrder_whenMenuHasNoBranch_thenSkips() {
        when(menuRepository.findById(3L)).thenReturn(Optional.of(menu(null)));

        stockConsumptionService.consumeMenuOrder(menuOrder(2));

        verify(recipeLineRepository, never()).findByProductIdIn(anyCollection());
    }

    @Test
    void reverseMenuOrder_whenSaleExists_thenRestoresBalance() {
        StockIngredient ingredient = ingredient("7.000");
        StockMovement sale = StockMovement.builder()
                .ingredientId(4L)
                .quantityDelta(new BigDecimal("-3.000"))
                .kind(StockMovementKind.SALE)
                .sourceType(StockSourceType.MENU_ORDER)
                .sourceId(8L)
                .build();
        when(movementRepository.findBySourceTypeAndSourceIdAndKind(
                StockSourceType.MENU_ORDER, 8L, StockMovementKind.SALE
        )).thenReturn(List.of(sale));
        when(ingredientRepository.lockByIds(anyCollection())).thenReturn(List.of(ingredient));
        when(movementRepository.existsByIngredientIdAndKindAndSourceTypeAndSourceId(
                4L, StockMovementKind.SALE_REVERSAL, StockSourceType.MENU_ORDER, 8L
        )).thenReturn(false);
        when(ingredientRepository.save(any(StockIngredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stockConsumptionService.reverseMenuOrder(menuOrder(2));

        assertThat(ingredient.getQuantity()).isEqualByComparingTo("10.000");
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(StockMovementKind.SALE_REVERSAL);
        assertThat(captor.getValue().getQuantityDelta()).isEqualByComparingTo("3.000");
    }

    private MenuOrder menuOrder(int quantity) {
        return MenuOrder.builder()
                .id(8L)
                .menuId(3L)
                .items(List.of(MenuOrderItem.builder().productId(5L).quantity(quantity).build()))
                .build();
    }

    private Menu menu(Long branchId) {
        return Menu.builder().menuId(3L).branchId(branchId).userId(1L).build();
    }

    private StockRecipeLine recipe(String perSale) {
        return StockRecipeLine.builder()
                .productId(5L)
                .ingredientId(4L)
                .quantityPerSale(new BigDecimal(perSale))
                .build();
    }

    private StockIngredient ingredient(String quantity) {
        return StockIngredient.builder()
                .id(4L)
                .userId(1L)
                .branchId(9L)
                .name("Un")
                .unit(StockUnit.KG)
                .quantity(new BigDecimal(quantity))
                .minQuantity(BigDecimal.ZERO)
                .build();
    }
}
