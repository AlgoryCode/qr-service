package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.StockIngredient;
import com.ael.algoryqrservice.model.StockRecipeLine;
import com.ael.algoryqrservice.model.dto.StockDtos;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.StockIngredientRepository;
import com.ael.algoryqrservice.repository.StockRecipeLineRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockRecipeService {

    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final StockIngredientRepository ingredientRepository;
    private final StockRecipeLineRepository recipeLineRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public StockDtos.RecipeResponse get(Long productId) {
        OwnedProduct owned = requireOwnedProduct(productId);
        return toResponse(owned);
    }

    @Transactional
    public StockDtos.RecipeResponse replace(Long productId, StockDtos.RecipeReplaceRequest request) {
        OwnedProduct owned = requireOwnedProduct(productId);
        List<StockDtos.RecipeLineRequest> lines = request.lines();
        if (lines.isEmpty()) {
            recipeLineRepository.deleteByProductId(productId);
            return emptyResponse(owned);
        }
        if (owned.menu().getBranchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menü bir şubeye bağlı değil");
        }
        ensureUnique(lines);
        Map<Long, StockIngredient> ingredients = loadIngredients(lines, owned.menu());
        recipeLineRepository.deleteByProductId(productId);
        recipeLineRepository.flush();
        for (StockDtos.RecipeLineRequest line : lines) {
            recipeLineRepository.save(StockRecipeLine.builder()
                    .productId(productId)
                    .ingredientId(line.ingredientId())
                    .quantityPerSale(StockQuantities.scale(line.quantityPerSale()))
                    .build());
        }
        return toResponse(owned, ingredients);
    }

    private void ensureUnique(List<StockDtos.RecipeLineRequest> lines) {
        HashSet<Long> seen = new HashSet<>();
        for (StockDtos.RecipeLineRequest line : lines) {
            if (!seen.add(line.ingredientId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aynı hammadde reçetede tekrarlanamaz");
            }
        }
    }

    private Map<Long, StockIngredient> loadIngredients(List<StockDtos.RecipeLineRequest> lines, Menu menu) {
        List<Long> ids = lines.stream().map(StockDtos.RecipeLineRequest::ingredientId).toList();
        Map<Long, StockIngredient> found = ingredientRepository.lockByIds(ids).stream()
                .filter(item -> !item.isDeleted())
                .filter(item -> menu.getUserId().equals(item.getUserId()))
                .filter(item -> menu.getBranchId().equals(item.getBranchId()))
                .collect(Collectors.toMap(StockIngredient::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hammadde bulunamadı");
        }
        return found;
    }

    private StockDtos.RecipeResponse toResponse(OwnedProduct owned) {
        List<StockRecipeLine> lines = recipeLineRepository.findByProductIdOrderByIdAsc(owned.product().getProductId());
        if (lines.isEmpty()) {
            return emptyResponse(owned);
        }
        Map<Long, StockIngredient> ingredients = ingredientRepository.findAllById(
                lines.stream().map(StockRecipeLine::getIngredientId).toList()
        ).stream().collect(Collectors.toMap(StockIngredient::getId, Function.identity()));
        return toResponse(owned, lines, ingredients);
    }

    private StockDtos.RecipeResponse toResponse(OwnedProduct owned, Map<Long, StockIngredient> ingredients) {
        List<StockRecipeLine> lines = recipeLineRepository.findByProductIdOrderByIdAsc(owned.product().getProductId());
        return toResponse(owned, lines, ingredients);
    }

    private StockDtos.RecipeResponse toResponse(
            OwnedProduct owned,
            List<StockRecipeLine> lines,
            Map<Long, StockIngredient> ingredients
    ) {
        List<StockDtos.RecipeLineResponse> mapped = lines.stream()
                .map(line -> toLine(line, ingredients.get(line.getIngredientId())))
                .toList();
        return new StockDtos.RecipeResponse(owned.product().getProductId(), owned.menu().getBranchId(), mapped);
    }

    private StockDtos.RecipeLineResponse toLine(StockRecipeLine line, StockIngredient ingredient) {
        if (ingredient == null) {
            return new StockDtos.RecipeLineResponse(line.getId(), line.getIngredientId(), null, null, line.getQuantityPerSale());
        }
        return new StockDtos.RecipeLineResponse(
                line.getId(),
                line.getIngredientId(),
                ingredient.getName(),
                ingredient.getUnit(),
                line.getQuantityPerSale()
        );
    }

    private StockDtos.RecipeResponse emptyResponse(OwnedProduct owned) {
        return new StockDtos.RecipeResponse(owned.product().getProductId(), owned.menu().getBranchId(), List.of());
    }

    private OwnedProduct requireOwnedProduct(Long productId) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        Menu menu = menuRepository.findById(product.getMenuId())
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        Long userId = securityUtils.getCurrentUserId();
        if (!userId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return new OwnedProduct(product, menu);
    }

    private record OwnedProduct(MenuProduct product, Menu menu) {
    }
}
