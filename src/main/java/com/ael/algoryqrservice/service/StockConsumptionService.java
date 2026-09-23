package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.StockIngredient;
import com.ael.algoryqrservice.model.StockMovement;
import com.ael.algoryqrservice.model.StockRecipeLine;
import com.ael.algoryqrservice.model.enums.StockMovementKind;
import com.ael.algoryqrservice.model.enums.StockSourceType;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.StockIngredientRepository;
import com.ael.algoryqrservice.repository.StockMovementRepository;
import com.ael.algoryqrservice.repository.StockRecipeLineRepository;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderItem;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockConsumptionService {

    private final MenuRepository menuRepository;
    private final MerchantRepository merchantRepository;
    private final StockRecipeLineRepository recipeLineRepository;
    private final StockIngredientRepository ingredientRepository;
    private final StockMovementRepository movementRepository;

    @Transactional
    public void consumeMenuOrder(MenuOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        Long branchId = menuRepository.findById(order.getMenuId()).map(Menu::getBranchId).orElse(null);
        if (branchId == null) {
            return;
        }
        consume(StockSourceType.MENU_ORDER, order.getId(), branchId, demandsFromMenu(order.getItems()));
    }

    @Transactional
    public void reverseMenuOrder(MenuOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        reverse(StockSourceType.MENU_ORDER, order.getId());
    }

    @Transactional
    public void consumeStoreOrder(StoreOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        Long branchId = storeBranchId(order.getMerchantId());
        if (branchId == null) {
            return;
        }
        consume(StockSourceType.STORE_ORDER, order.getId(), branchId, demandsFromStore(order.getItems()));
    }

    @Transactional
    public void reverseStoreOrder(StoreOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        reverse(StockSourceType.STORE_ORDER, order.getId());
    }

    private Long storeBranchId(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null) {
            return null;
        }
        Long menuBranch = menuRepository.findById(merchant.getCatalogMenuId()).map(Menu::getBranchId).orElse(null);
        if (menuBranch != null) {
            return menuBranch;
        }
        return merchant.getBranchId();
    }

    private void consume(StockSourceType sourceType, Long sourceId, Long branchId, Map<Long, Integer> productQty) {
        if (productQty.isEmpty()) {
            return;
        }
        List<StockRecipeLine> lines = recipeLineRepository.findByProductIdIn(productQty.keySet());
        Map<Long, BigDecimal> required = requiredByIngredient(lines, productQty);
        if (required.isEmpty()) {
            return;
        }
        Map<Long, StockIngredient> ingredients = lockForBranch(required, branchId);
        Map<Long, BigDecimal> applicable = applicable(required, ingredients);
        if (applicable.isEmpty()) {
            return;
        }
        assertAvailable(sourceType, sourceId, applicable, ingredients);
        applySales(sourceType, sourceId, applicable, ingredients);
    }

    private Map<Long, StockIngredient> lockForBranch(Map<Long, BigDecimal> required, Long branchId) {
        Map<Long, StockIngredient> ingredients = new HashMap<>();
        for (StockIngredient ingredient : ingredientRepository.lockByIds(required.keySet())) {
            if (!ingredient.isDeleted() && branchId.equals(ingredient.getBranchId())) {
                ingredients.put(ingredient.getId(), ingredient);
            }
        }
        return ingredients;
    }

    private Map<Long, BigDecimal> applicable(Map<Long, BigDecimal> required, Map<Long, StockIngredient> ingredients) {
        Map<Long, BigDecimal> applicable = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
            if (ingredients.containsKey(entry.getKey())) {
                applicable.put(entry.getKey(), entry.getValue());
            }
        }
        return applicable;
    }

    private void assertAvailable(
            StockSourceType sourceType,
            Long sourceId,
            Map<Long, BigDecimal> applicable,
            Map<Long, StockIngredient> ingredients
    ) {
        for (Map.Entry<Long, BigDecimal> entry : applicable.entrySet()) {
            if (alreadyPosted(entry.getKey(), StockMovementKind.SALE, sourceType, sourceId)) {
                continue;
            }
            StockIngredient ingredient = ingredients.get(entry.getKey());
            if (ingredient.getQuantity().compareTo(entry.getValue()) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yetersiz stok: " + ingredient.getName());
            }
        }
    }

    private void applySales(
            StockSourceType sourceType,
            Long sourceId,
            Map<Long, BigDecimal> applicable,
            Map<Long, StockIngredient> ingredients
    ) {
        for (Map.Entry<Long, BigDecimal> entry : applicable.entrySet()) {
            if (alreadyPosted(entry.getKey(), StockMovementKind.SALE, sourceType, sourceId)) {
                continue;
            }
            StockIngredient ingredient = ingredients.get(entry.getKey());
            BigDecimal next = StockQuantities.scale(ingredient.getQuantity().subtract(entry.getValue()));
            ingredient.setQuantity(next);
            ingredientRepository.save(ingredient);
            saveMovement(ingredient, StockMovementKind.SALE, entry.getValue().negate(), next, sourceType, sourceId);
        }
    }

    private void reverse(StockSourceType sourceType, Long sourceId) {
        List<StockMovement> sales = movementRepository.findBySourceTypeAndSourceIdAndKind(
                sourceType,
                sourceId,
                StockMovementKind.SALE
        );
        if (sales.isEmpty()) {
            return;
        }
        Map<Long, StockIngredient> locked = lockAll(sales);
        for (StockMovement sale : sales) {
            restore(sale, sourceType, sourceId, locked.get(sale.getIngredientId()));
        }
    }

    private Map<Long, StockIngredient> lockAll(List<StockMovement> sales) {
        List<Long> ids = sales.stream().map(StockMovement::getIngredientId).distinct().toList();
        Map<Long, StockIngredient> locked = new HashMap<>();
        for (StockIngredient ingredient : ingredientRepository.lockByIds(ids)) {
            locked.put(ingredient.getId(), ingredient);
        }
        return locked;
    }

    private void restore(StockMovement sale, StockSourceType sourceType, Long sourceId, StockIngredient ingredient) {
        if (ingredient == null || alreadyPosted(ingredient.getId(), StockMovementKind.SALE_REVERSAL, sourceType, sourceId)) {
            return;
        }
        BigDecimal delta = sale.getQuantityDelta().negate();
        BigDecimal next = StockQuantities.scale(ingredient.getQuantity().add(delta));
        ingredient.setQuantity(next);
        ingredientRepository.save(ingredient);
        saveMovement(ingredient, StockMovementKind.SALE_REVERSAL, delta, next, sourceType, sourceId);
    }

    private boolean alreadyPosted(Long ingredientId, StockMovementKind kind, StockSourceType sourceType, Long sourceId) {
        return movementRepository.existsByIngredientIdAndKindAndSourceTypeAndSourceId(
                ingredientId,
                kind,
                sourceType,
                sourceId
        );
    }

    private Map<Long, BigDecimal> requiredByIngredient(List<StockRecipeLine> lines, Map<Long, Integer> productQty) {
        Map<Long, BigDecimal> required = new HashMap<>();
        for (StockRecipeLine line : lines) {
            int sold = productQty.getOrDefault(line.getProductId(), 0);
            if (sold <= 0) {
                continue;
            }
            BigDecimal need = StockQuantities.scale(line.getQuantityPerSale().multiply(BigDecimal.valueOf(sold)));
            required.merge(line.getIngredientId(), need, BigDecimal::add);
        }
        return required;
    }

    private void saveMovement(
            StockIngredient ingredient,
            StockMovementKind kind,
            BigDecimal delta,
            BigDecimal balanceAfter,
            StockSourceType sourceType,
            Long sourceId
    ) {
        movementRepository.save(StockMovement.builder()
                .ingredientId(ingredient.getId())
                .userId(ingredient.getUserId())
                .branchId(ingredient.getBranchId())
                .kind(kind)
                .quantityDelta(StockQuantities.scale(delta))
                .balanceAfter(StockQuantities.scale(balanceAfter))
                .sourceType(sourceType)
                .sourceId(sourceId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Map<Long, Integer> demandsFromMenu(List<MenuOrderItem> items) {
        Map<Long, Integer> productQty = new HashMap<>();
        if (items == null) {
            return productQty;
        }
        for (MenuOrderItem item : items) {
            if (item.getQuantity() > 0) {
                productQty.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        return productQty;
    }

    private Map<Long, Integer> demandsFromStore(List<StoreOrderItem> items) {
        Map<Long, Integer> productQty = new HashMap<>();
        if (items == null) {
            return productQty;
        }
        for (StoreOrderItem item : items) {
            if (item.getQuantity() > 0) {
                productQty.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        return productQty;
    }
}
