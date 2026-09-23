package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.StockIngredient;
import com.ael.algoryqrservice.model.StockMovement;
import com.ael.algoryqrservice.model.dto.StockDtos;
import com.ael.algoryqrservice.model.enums.StockMovementKind;
import com.ael.algoryqrservice.model.enums.StockSourceType;
import com.ael.algoryqrservice.repository.StockIngredientRepository;
import com.ael.algoryqrservice.repository.StockMovementRepository;
import com.ael.algoryqrservice.repository.StockRecipeLineRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockIngredientService {

    private final StockIngredientRepository ingredientRepository;
    private final StockMovementRepository movementRepository;
    private final StockRecipeLineRepository recipeLineRepository;
    private final BranchService branchService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public StockDtos.IngredientPageResponse list(Long branchId, boolean lowOnly, int page, int size) {
        branchService.requireOwned(branchId);
        Long userId = securityUtils.getCurrentUserId();
        Page<StockIngredient> result = ingredientRepository.search(
                branchId,
                userId,
                lowOnly,
                PageRequest.of(safePage(page), safeSize(size), Sort.by("name").ascending())
        );
        return new StockDtos.IngredientPageResponse(
                result.getContent().stream().map(this::toIngredient).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public StockDtos.IngredientResponse create(Long branchId, StockDtos.IngredientRequest request) {
        var branch = branchService.requireOwned(branchId);
        BigDecimal opening = StockQuantities.scale(request.quantity());
        StockIngredient saved = ingredientRepository.save(StockIngredient.builder()
                .userId(branch.getUserId())
                .branchId(branch.getId())
                .name(request.name().trim())
                .unit(request.unit())
                .quantity(opening)
                .minQuantity(StockQuantities.scale(request.minQuantity()))
                .deleted(false)
                .build());
        if (opening.signum() > 0) {
            writeMovement(saved, StockMovementKind.IN, opening, opening, null);
        }
        return toIngredient(saved);
    }

    @Transactional(readOnly = true)
    public StockDtos.IngredientResponse get(Long ingredientId) {
        return toIngredient(requireOwned(ingredientId));
    }

    @Transactional
    public StockDtos.IngredientResponse update(Long ingredientId, StockDtos.IngredientUpdateRequest request) {
        StockIngredient ingredient = requireOwned(ingredientId);
        ingredient.setName(request.name().trim());
        ingredient.setUnit(request.unit());
        ingredient.setMinQuantity(StockQuantities.scale(request.minQuantity()));
        return toIngredient(ingredientRepository.save(ingredient));
    }

    @Transactional
    public void delete(Long ingredientId) {
        StockIngredient ingredient = requireOwned(ingredientId);
        recipeLineRepository.deleteByIngredientId(ingredientId);
        ingredient.setDeleted(true);
        ingredientRepository.save(ingredient);
    }

    @Transactional(readOnly = true)
    public StockDtos.MovementPageResponse listMovements(Long ingredientId, int page, int size) {
        StockIngredient ingredient = requireOwned(ingredientId);
        Page<StockMovement> result = movementRepository.findByIngredientIdOrderByCreatedAtDescIdDesc(
                ingredient.getId(),
                PageRequest.of(safePage(page), safeSize(size))
        );
        return new StockDtos.MovementPageResponse(
                result.getContent().stream().map(this::toMovement).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public StockDtos.MovementResponse recordMovement(Long ingredientId, StockDtos.MovementRequest request) {
        if (request.kind() != StockMovementKind.IN
                && request.kind() != StockMovementKind.OUT
                && request.kind() != StockMovementKind.ADJUST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu hareket türü elle kaydedilemez");
        }
        StockIngredient ingredient = requireOwned(ingredientId);
        BigDecimal amount = StockQuantities.scale(request.quantity());
        BigDecimal delta = deltaFor(request.kind(), amount, ingredient.getQuantity());
        if (delta.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakiye değişmedi");
        }
        BigDecimal next = StockQuantities.scale(ingredient.getQuantity().add(delta));
        if (next.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yetersiz stok: " + ingredient.getName());
        }
        ingredient.setQuantity(next);
        ingredientRepository.save(ingredient);
        return toMovement(writeMovement(ingredient, request.kind(), delta, next, blankToNull(request.note())));
    }

    private BigDecimal deltaFor(StockMovementKind kind, BigDecimal amount, BigDecimal current) {
        if (kind == StockMovementKind.ADJUST) {
            return StockQuantities.scale(amount.subtract(current));
        }
        if (amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Miktar sıfırdan büyük olmalı");
        }
        if (kind == StockMovementKind.OUT) {
            return amount.negate();
        }
        return amount;
    }

    private StockMovement writeMovement(
            StockIngredient ingredient,
            StockMovementKind kind,
            BigDecimal delta,
            BigDecimal balanceAfter,
            String note
    ) {
        return movementRepository.save(StockMovement.builder()
                .ingredientId(ingredient.getId())
                .userId(ingredient.getUserId())
                .branchId(ingredient.getBranchId())
                .kind(kind)
                .quantityDelta(StockQuantities.scale(delta))
                .balanceAfter(StockQuantities.scale(balanceAfter))
                .sourceType(StockSourceType.MANUAL)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private StockIngredient requireOwned(Long ingredientId) {
        Long userId = securityUtils.getCurrentUserId();
        return ingredientRepository.findByIdAndUserIdAndDeletedFalse(ingredientId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hammadde bulunamadı"));
    }

    private StockDtos.IngredientResponse toIngredient(StockIngredient ingredient) {
        return new StockDtos.IngredientResponse(
                ingredient.getId(),
                ingredient.getBranchId(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.getQuantity(),
                ingredient.getMinQuantity(),
                ingredient.getQuantity().compareTo(ingredient.getMinQuantity()) <= 0
        );
    }

    private StockDtos.MovementResponse toMovement(StockMovement movement) {
        return new StockDtos.MovementResponse(
                movement.getId(),
                movement.getIngredientId(),
                movement.getKind(),
                movement.getQuantityDelta(),
                movement.getBalanceAfter(),
                movement.getSourceType(),
                movement.getSourceId(),
                movement.getNote(),
                movement.getCreatedAt()
        );
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
