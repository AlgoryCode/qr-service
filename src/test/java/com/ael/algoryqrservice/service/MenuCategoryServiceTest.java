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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuCategoryServiceTest {

    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private AppProperties appProperties;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MenuCategoryService menuCategoryService;

    @BeforeEach
    void setUp() {
        AppProperties.MenuSettings menuSettings = new AppProperties.MenuSettings();
        menuSettings.setCategoryMaxDepth(10);
        when(appProperties.getMenu()).thenReturn(menuSettings);
    }

    @Test
    void createCategory_whenNestedLevels_thenBuildTree() {
        Menu menu = Menu.builder().menuId(1L).userId(5L).build();
        when(menuRepository.findById(1L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUser()).thenReturn(com.ael.algoryqrservice.model.User.builder().id(5L).build());
        when(menuCategoryRepository.existsByMenuIdAndParentIdAndNameIgnoreCaseAndDeletedFalse(any(), any(), any())).thenReturn(false);
        when(menuCategoryRepository.countByMenuIdAndParentIdAndDeletedFalse(1L, null)).thenReturn(0L);
        when(menuCategoryRepository.save(any(MenuCategory.class))).thenAnswer(invocation -> {
            MenuCategory category = invocation.getArgument(0);
            if (category.getCategoryId() == null) {
                category.setCategoryId(category.getParentId() == null ? 10L : 11L);
            }
            return category;
        });
        when(menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(1L))
                .thenReturn(List.of(
                        MenuCategory.builder().categoryId(10L).menuId(1L).name("Icecekler").sortOrder(0).build(),
                        MenuCategory.builder().categoryId(11L).menuId(1L).parentId(10L).name("Soguk").sortOrder(0).build()
                ));

        MenuDtos.MenuCategoryRequest rootRequest = new MenuDtos.MenuCategoryRequest();
        rootRequest.setName("Icecekler");
        MenuDtos.MenuCategoryResponse root = menuCategoryService.createCategory(1L, rootRequest);

        MenuDtos.MenuCategoryRequest childRequest = new MenuDtos.MenuCategoryRequest();
        childRequest.setName("Soguk");
        childRequest.setParentId(10L);
        when(menuCategoryRepository.findByCategoryIdAndDeletedFalse(10L))
                .thenReturn(Optional.of(MenuCategory.builder().categoryId(10L).menuId(1L).name("Icecekler").build()));
        when(menuCategoryRepository.countByMenuIdAndParentIdAndDeletedFalse(1L, 10L)).thenReturn(0L);
        menuCategoryService.createCategory(1L, childRequest);

        List<MenuDtos.MenuCategoryResponse> tree = menuCategoryService.listCategoryTree(1L);
        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().getName()).isEqualTo("Icecekler");
        assertThat(tree.getFirst().getChildren()).hasSize(1);
        assertThat(tree.getFirst().getChildren().getFirst().getName()).isEqualTo("Soguk");
        assertThat(root.getName()).isEqualTo("Icecekler");
    }

    @Test
    void deleteCategory_whenChildExists_thenBadRequest() {
        Menu menu = Menu.builder().menuId(1L).userId(5L).build();
        MenuCategory category = MenuCategory.builder().categoryId(10L).menuId(1L).name("Icecekler").build();
        when(menuCategoryRepository.findByCategoryIdAndDeletedFalse(10L)).thenReturn(Optional.of(category));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUser()).thenReturn(com.ael.algoryqrservice.model.User.builder().id(5L).build());
        when(menuCategoryRepository.existsByParentIdAndDeletedFalse(10L)).thenReturn(true);

        assertThatThrownBy(() -> menuCategoryService.deleteCategory(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Alt kategorisi");
    }

    @Test
    void findOrCreateRootCategory_whenMissing_thenPersistCategory() {
        when(menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(1L)).thenReturn(List.of());
        when(menuCategoryRepository.countByMenuIdAndParentIdAndDeletedFalse(1L, null)).thenReturn(0L);
        when(menuCategoryRepository.save(any(MenuCategory.class))).thenAnswer(invocation -> {
            MenuCategory category = invocation.getArgument(0);
            category.setCategoryId(99L);
            return category;
        });

        Long categoryId = menuCategoryService.findOrCreateRootCategory(1L, "Tatl?lar");

        assertThat(categoryId).isEqualTo(99L);
        ArgumentCaptor<MenuCategory> captor = ArgumentCaptor.forClass(MenuCategory.class);
        verify(menuCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Tatl?lar");
        assertThat(captor.getValue().getParentId()).isNull();
    }
}
