package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.StockDtos;
import com.ael.algoryqrservice.model.enums.StockUnit;
import com.ael.algoryqrservice.repository.StockIngredientRepository;
import com.ael.algoryqrservice.repository.StockMovementRepository;
import com.ael.algoryqrservice.repository.StockRecipeLineRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockIngredientServiceTest {

    @Mock
    private StockIngredientRepository ingredientRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private StockRecipeLineRepository recipeLineRepository;
    @Mock
    private BranchService branchService;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private StockIngredientService stockIngredientService;

    @Test
    void create_whenBranchNotOwned_thenNotFound() {
        when(branchService.requireOwned(9L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));

        assertThatThrownBy(() -> stockIngredientService.create(9L, request()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Şube bulunamadı");
        verifyNoInteractions(ingredientRepository);
    }

    @Test
    void get_whenIngredientNotOwned_thenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(ingredientRepository.findByIdAndUserIdAndDeletedFalse(4L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockIngredientService.get(4L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hammadde bulunamadı");
    }

    private StockDtos.IngredientRequest request() {
        return new StockDtos.IngredientRequest("Un", StockUnit.KG, new BigDecimal("5"), BigDecimal.ONE);
    }
}
