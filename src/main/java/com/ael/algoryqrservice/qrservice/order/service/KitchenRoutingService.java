package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.MenuCategory;
import com.ael.algoryqrservice.qrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.qrservice.product.model.MenuProduct;
import com.ael.algoryqrservice.qrservice.product.repository.MenuProductRepository;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hangi ürünün mutfağa gitmeyeceğini söyler: şubenin mutfağı açıkken, kategorisi "mutfağa gitmez" işaretli
 * ürünler mutfak fişine düşmez. Mutfak kapalıysa ayrım yapılmaz.
 */
@Service
@RequiredArgsConstructor
public class KitchenRoutingService {

    private final MenuRepository menuRepository;
    private final BranchRepository branchRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    @Transactional(readOnly = true)
    public Set<Long> directProductIds(Long menuId, Collection<Long> productIds) {
        if (productIds.isEmpty() || !kitchenEnabled(menuId)) {
            return Set.of();
        }
        Map<Long, MenuProduct> products = menuProductRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(MenuProduct::getProductId, Function.identity()));
        Set<Long> subIds = products.values().stream()
                .map(MenuProduct::getSubCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Long> mainBySub = menuSubCategoryRepository.findAllById(subIds).stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, MenuSubCategory::getMenuCategoryId));
        Set<Long> skippingMains = menuCategoryRepository.findAllById(Set.copyOf(mainBySub.values())).stream()
                .filter(MenuCategory::isSkipKitchen)
                .map(MenuCategory::getId)
                .collect(Collectors.toSet());
        return products.values().stream()
                .filter(product -> product.getSubCategoryId() != null
                        && skippingMains.contains(mainBySub.get(product.getSubCategoryId())))
                .map(MenuProduct::getProductId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean kitchenEnabled(Long menuId) {
        return menuRepository.findById(menuId)
                .map(Menu::getBranchId)
                .flatMap(branchRepository::findById)
                .map(Branch::isKitchenEnabled)
                .orElse(false);
    }
}
