package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.MenuCategoryService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/menu/{menuId}")
@RequiredArgsConstructor
public class MenuCategoryController {

    private final MenuCategoryService menuCategoryService;
    private final MenuRepository menuRepository;
    private final SecurityUtils securityUtils;

    @GetMapping("/categories")
    public List<TaxonomyDtos.MainCategoryResponse> list(@PathVariable Long menuId) {
        requireOwnedMenu(menuId);
        return menuCategoryService.listTaxonomy(menuId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyDtos.MainCategoryResponse createCategory(
            @PathVariable Long menuId,
            @RequestBody TaxonomyDtos.MainCategoryRequest request
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.createCategory(menuId, request);
    }

    @PutMapping("/categories/{categoryId}")
    public TaxonomyDtos.MainCategoryResponse updateCategory(
            @PathVariable Long menuId,
            @PathVariable Long categoryId,
            @RequestBody TaxonomyDtos.MainCategoryUpdateRequest request
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.updateCategory(menuId, categoryId, request);
    }

    @PostMapping("/categories/{categoryId}/cover")
    public TaxonomyDtos.MainCategoryResponse uploadCover(
            @PathVariable Long menuId,
            @PathVariable Long categoryId,
            @RequestParam("file") MultipartFile file
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.uploadCover(menuId, categoryId, file);
    }

    @DeleteMapping("/categories/{categoryId}/cover")
    public TaxonomyDtos.MainCategoryResponse deleteCover(
            @PathVariable Long menuId,
            @PathVariable Long categoryId
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.clearCover(menuId, categoryId);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long menuId, @PathVariable Long categoryId) {
        requireOwnedMenu(menuId);
        menuCategoryService.deleteCategory(menuId, categoryId);
    }

    @PostMapping("/categories/{categoryId}/subs")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyDtos.SubCategoryResponse createSub(
            @PathVariable Long menuId,
            @PathVariable Long categoryId,
            @RequestBody TaxonomyDtos.SubCategoryRequest request
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.createSub(menuId, categoryId, request);
    }

    @PutMapping("/subs/{subId}")
    public TaxonomyDtos.SubCategoryResponse updateSub(
            @PathVariable Long menuId,
            @PathVariable Long subId,
            @RequestBody TaxonomyDtos.SubCategoryUpdateRequest request
    ) {
        requireOwnedMenu(menuId);
        return menuCategoryService.updateSub(menuId, subId, request);
    }

    @DeleteMapping("/subs/{subId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSub(@PathVariable Long menuId, @PathVariable Long subId) {
        requireOwnedMenu(menuId);
        menuCategoryService.deleteSub(menuId, subId);
    }

    private void requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
    }
}
