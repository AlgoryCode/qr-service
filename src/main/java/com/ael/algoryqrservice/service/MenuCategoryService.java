package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MenuCategoryService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final AppProperties appProperties;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuCategoryResponse> listCategoryTree(Long menuId) {
        ensureOwnedMenu(menuId);
        return buildCategoryTree(menuId);
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuCategoryResponse> listPublicCategoryTree(Long menuId) {
        return buildCategoryTree(menuId);
    }

    @Transactional
    public Long findOrCreateRootCategory(Long menuId, String categoryName) {
        String name = requireName(categoryName);
        return menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId).stream()
                .filter(category -> category.getParentId() == null && category.getName().equalsIgnoreCase(name))
                .map(MenuCategory::getCategoryId)
                .findFirst()
                .orElseGet(() -> menuCategoryRepository.save(MenuCategory.builder()
                        .menuId(menuId)
                        .parentId(null)
                        .name(name)
                        .sortOrder(nextSortOrder(menuId, null))
                        .build()).getCategoryId());
    }

    @Transactional
    public MenuDtos.MenuCategoryResponse createCategory(Long menuId, MenuDtos.MenuCategoryRequest request) {
        ensureOwnedMenu(menuId);
        String name = requireName(request.getName());
        Long parentId = request.getParentId();
        validateParent(menuId, parentId, null);
        validateDepth(parentId, menuId);
        ensureUniqueName(menuId, parentId, name, null);

        MenuCategory category = MenuCategory.builder()
                .menuId(menuId)
                .parentId(parentId)
                .name(name)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(menuId, parentId))
                .build();

        return toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public MenuDtos.MenuCategoryResponse updateCategory(Long categoryId, MenuDtos.MenuCategoryUpdateRequest request) {
        MenuCategory category = menuCategoryRepository.findByCategoryIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadi"));
        ensureOwnedMenu(category.getMenuId());

        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            Long parentForUnique = request.getParentId() != null ? request.getParentId() : category.getParentId();
            ensureUniqueName(category.getMenuId(), parentForUnique, name, categoryId);
            category.setName(name);
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getParentId() != null || category.getParentId() != null) {
            Long nextParentId = request.getParentId();
            if (!Objects.equals(nextParentId, category.getParentId())) {
                validateParent(category.getMenuId(), nextParentId, categoryId);
                validateDepthForMove(categoryId, nextParentId, category.getMenuId());
                ensureUniqueName(category.getMenuId(), nextParentId, category.getName(), categoryId);
                category.setParentId(nextParentId);
            }
        }

        return toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        MenuCategory category = menuCategoryRepository.findByCategoryIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadi"));
        ensureOwnedMenu(category.getMenuId());

        if (menuCategoryRepository.existsByParentIdAndDeletedFalse(categoryId)) {
            throw new BadRequestException("Alt kategorisi olan kategori silinemez");
        }
        if (menuProductRepository.countByCategoryIdAndDeletedFalse(categoryId) > 0) {
            throw new BadRequestException("Urun bagli kategoriler silinemez");
        }

        category.setDeleted(true);
        menuCategoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public MenuCategory requireCategoryForMenu(Long menuId, Long categoryId) {
        MenuCategory category = menuCategoryRepository.findByCategoryIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori bulunamadi"));
        if (!menuId.equals(category.getMenuId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori bu menuye ait degil");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuCategory> loadCategoryMap(Long menuId) {
        Map<Long, MenuCategory> map = new HashMap<>();
        for (MenuCategory category : menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId)) {
            map.put(category.getCategoryId(), category);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String resolveCategoryPath(Long categoryId, Map<Long, MenuCategory> categoryMap) {
        if (categoryId == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        Long currentId = categoryId;
        while (currentId != null) {
            MenuCategory category = categoryMap.get(currentId);
            if (category == null) {
                break;
            }
            parts.addFirst(category.getName());
            currentId = category.getParentId();
        }
        return parts.isEmpty() ? null : String.join(" > ", parts);
    }

    @Transactional(readOnly = true)
    public String resolveCategoryName(Long categoryId, Map<Long, MenuCategory> categoryMap) {
        if (categoryId == null) {
            return null;
        }
        MenuCategory category = categoryMap.get(categoryId);
        return category == null ? null : category.getName();
    }

    private Menu ensureOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu bulunamadi"));
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menuye erisim yetkiniz yok");
        }
        return menu;
    }

    private void validateParent(Long menuId, Long parentId, Long movingCategoryId) {
        if (parentId == null) {
            return;
        }
        MenuCategory parent = menuCategoryRepository.findByCategoryIdAndDeletedFalse(parentId)
                .orElseThrow(() -> new BadRequestException("Ust kategori bulunamadi"));
        if (!menuId.equals(parent.getMenuId())) {
            throw new BadRequestException("Ust kategori bu menuye ait degil");
        }
        if (movingCategoryId != null && parentId.equals(movingCategoryId)) {
            throw new BadRequestException("Kategori kendi altina tasinamaz");
        }
        if (movingCategoryId != null && isDescendant(movingCategoryId, parentId, menuId)) {
            throw new BadRequestException("Kategori kendi alt kategorisine tasinamaz");
        }
    }

    private void validateDepth(Long parentId, Long menuId) {
        int depth = parentDepth(parentId, menuId) + 1;
        if (depth > maxDepth()) {
            throw new BadRequestException("Kategori derinligi en fazla " + maxDepth() + " olabilir");
        }
    }

    private void validateDepthForMove(Long categoryId, Long parentId, Long menuId) {
        int subtreeHeight = subtreeHeight(categoryId, menuId);
        int parentDepth = parentDepth(parentId, menuId);
        if (parentDepth + 1 + subtreeHeight > maxDepth()) {
            throw new BadRequestException("Kategori derinligi en fazla " + maxDepth() + " olabilir");
        }
    }

    private int parentDepth(Long parentId, Long menuId) {
        if (parentId == null) {
            return 0;
        }
        Map<Long, MenuCategory> categoryMap = loadCategoryMap(menuId);
        int depth = 0;
        Long currentId = parentId;
        while (currentId != null) {
            MenuCategory category = categoryMap.get(currentId);
            if (category == null) {
                break;
            }
            depth++;
            currentId = category.getParentId();
        }
        return depth;
    }

    private int subtreeHeight(Long categoryId, Long menuId) {
        List<MenuCategory> categories = menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId);
        Map<Long, List<MenuCategory>> childrenByParent = new HashMap<>();
        for (MenuCategory category : categories) {
            Long parentKey = category.getParentId();
            childrenByParent.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(category);
        }
        return height(categoryId, childrenByParent);
    }

    private int height(Long categoryId, Map<Long, List<MenuCategory>> childrenByParent) {
        List<MenuCategory> children = childrenByParent.getOrDefault(categoryId, List.of());
        if (children.isEmpty()) {
            return 1;
        }
        int maxChildHeight = 0;
        for (MenuCategory child : children) {
            maxChildHeight = Math.max(maxChildHeight, height(child.getCategoryId(), childrenByParent));
        }
        return 1 + maxChildHeight;
    }

    private boolean isDescendant(Long ancestorId, Long candidateId, Long menuId) {
        Map<Long, MenuCategory> categoryMap = loadCategoryMap(menuId);
        Long currentId = candidateId;
        while (currentId != null) {
            if (ancestorId.equals(currentId)) {
                return true;
            }
            MenuCategory category = categoryMap.get(currentId);
            if (category == null) {
                break;
            }
            currentId = category.getParentId();
        }
        return false;
    }

    private void ensureUniqueName(Long menuId, Long parentId, String name, Long excludeCategoryId) {
        boolean exists = excludeCategoryId == null
                ? menuCategoryRepository.existsByMenuIdAndParentIdAndNameIgnoreCaseAndDeletedFalse(menuId, parentId, name)
                : menuCategoryRepository.existsByMenuIdAndParentIdAndNameIgnoreCaseAndDeletedFalseAndCategoryIdNot(
                menuId, parentId, name, excludeCategoryId);
        if (exists) {
            throw new BadRequestException("Bu seviyede ayni isimde kategori zaten var");
        }
    }

    private int nextSortOrder(Long menuId, Long parentId) {
        return (int) menuCategoryRepository.countByMenuIdAndParentIdAndDeletedFalse(menuId, parentId);
    }

    private int maxDepth() {
        return appProperties.getMenu().getCategoryMaxDepth();
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Kategori adi zorunludur");
        }
        return name.trim();
    }

    private List<MenuDtos.MenuCategoryResponse> buildCategoryTree(Long menuId) {
        return buildTree(menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId));
    }

    private List<MenuDtos.MenuCategoryResponse> buildTree(List<MenuCategory> categories) {
        Map<Long, MenuDtos.MenuCategoryResponse> nodes = new HashMap<>();
        for (MenuCategory category : categories) {
            nodes.put(category.getCategoryId(), toResponse(category));
        }
        List<MenuDtos.MenuCategoryResponse> roots = new ArrayList<>();
        for (MenuCategory category : categories) {
            MenuDtos.MenuCategoryResponse node = nodes.get(category.getCategoryId());
            if (category.getParentId() == null) {
                roots.add(node);
                continue;
            }
            MenuDtos.MenuCategoryResponse parent = nodes.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuDtos.MenuCategoryResponse> nodes) {
        nodes.sort(Comparator.comparingInt(MenuDtos.MenuCategoryResponse::getSortOrder)
                .thenComparing(MenuDtos.MenuCategoryResponse::getCategoryId));
        for (MenuDtos.MenuCategoryResponse node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private MenuDtos.MenuCategoryResponse toResponse(MenuCategory category) {
        return MenuDtos.MenuCategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .menuId(category.getMenuId())
                .parentId(category.getParentId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .children(new ArrayList<>())
                .build();
    }
}
