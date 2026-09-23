package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.StockMovementKind;
import com.ael.algoryqrservice.model.enums.StockSourceType;
import com.ael.algoryqrservice.model.enums.StockUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    public record IngredientRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull StockUnit unit,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minQuantity
    ) {
    }

    public record IngredientUpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull StockUnit unit,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minQuantity
    ) {
    }

    public record IngredientResponse(
            Long id,
            Long branchId,
            String name,
            StockUnit unit,
            BigDecimal quantity,
            BigDecimal minQuantity,
            boolean lowStock
    ) {
    }

    public record IngredientPageResponse(
            List<IngredientResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record MovementRequest(
            @NotNull StockMovementKind kind,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @Size(max = 500) String note
    ) {
    }

    public record MovementResponse(
            Long id,
            Long ingredientId,
            StockMovementKind kind,
            BigDecimal quantityDelta,
            BigDecimal balanceAfter,
            StockSourceType sourceType,
            Long sourceId,
            String note,
            LocalDateTime createdAt
    ) {
    }

    public record MovementPageResponse(
            List<MovementResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record RecipeLineRequest(
            @NotNull Long ingredientId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantityPerSale
    ) {
    }

    public record RecipeReplaceRequest(
            @NotNull @Valid List<RecipeLineRequest> lines
    ) {
    }

    public record RecipeLineResponse(
            Long id,
            Long ingredientId,
            String ingredientName,
            StockUnit unit,
            BigDecimal quantityPerSale
    ) {
    }

    public record RecipeResponse(
            Long productId,
            Long branchId,
            List<RecipeLineResponse> lines
    ) {
    }
}
