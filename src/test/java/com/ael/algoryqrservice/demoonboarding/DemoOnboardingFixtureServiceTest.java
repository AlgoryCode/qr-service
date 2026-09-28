package com.ael.algoryqrservice.demoonboarding;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.BranchQuotaService;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.service.QrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoOnboardingFixtureServiceTest {

    @Mock
    private DemoOnboardingAssignmentRepository assignmentRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private BranchQuotaService branchQuotaService;
    @Mock
    private QrService qrService;
    @Mock
    private MenuCatalogCloneService menuCatalogCloneService;
    @Mock
    private MenuThemeService menuThemeService;
    @Mock
    private DemoOnboardingSalesSeedService salesSeedService;

    private DemoOnboardingFixtureProperties properties;
    private DemoOnboardingFixtureService service;

    @BeforeEach
    void setUp() {
        properties = new DemoOnboardingFixtureProperties();
        properties.setEnabled(true);
        properties.setCatalogTemplateUserId(100L);
        properties.setCatalogTemplateBranchId(10L);
        properties.setCatalogTemplateMenuId(20L);
        properties.setThemeTemplateUserId(200L);
        properties.setThemeTemplateMenuId(30L);
        service = new DemoOnboardingFixtureService(
                properties,
                assignmentRepository,
                branchRepository,
                menuRepository,
                restaurantTableRepository,
                branchQuotaService,
                qrService,
                menuCatalogCloneService,
                menuThemeService,
                salesSeedService
        );
    }

    @Test
    void assign_whenNotReady_thenSkips() {
        properties.setCatalogTemplateMenuId(0L);

        service.assign(7L, "demo");

        verifyNoInteractions(assignmentRepository, menuThemeService, qrService);
    }

    @Test
    void assign_whenReady_thenAppliesThemeClonesCatalogAndSeedsSales() throws Exception {
        Branch catalogBranch = Branch.builder().id(10L).userId(100L).name("Roof").build();
        Menu catalogMenu = Menu.builder()
                .menuId(20L)
                .userId(100L)
                .branchId(10L)
                .businessName("Aya Roof Lounge")
                .active(true)
                .deleted(false)
                .build();
        Menu themeMenu = Menu.builder()
                .menuId(30L)
                .userId(200L)
                .themeId("maison-noir")
                .coverUrl("https://cdn/cover.jpg")
                .active(true)
                .deleted(false)
                .build();
        Menu created = Menu.builder().menuId(40L).userId(7L).branchId(11L).build();

        when(assignmentRepository.existsByUserId(7L)).thenReturn(false);
        when(branchRepository.findByIdAndUserIdAndDeletedFalse(10L, 100L)).thenReturn(Optional.of(catalogBranch));
        when(menuRepository.findById(20L)).thenReturn(Optional.of(catalogMenu));
        when(menuRepository.findById(30L)).thenReturn(Optional.of(themeMenu));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(11L);
            return branch;
        });
        when(qrService.createQR(any(QrRequest.class), eq(7L)))
                .thenReturn(QrResponse.builder().menuId(40L).build());
        when(menuRepository.findById(40L)).thenReturn(Optional.of(created));
        when(menuCatalogCloneService.cloneFromTemplate(created, 20L, 100L)).thenReturn(Map.of(1L, 2L));
        when(restaurantTableRepository.findFirstByMenuIdAndActiveTrueAndDeletedFalseOrderByTableNumberAscNameAsc(40L))
                .thenReturn(Optional.empty());
        when(restaurantTableRepository.save(any())).thenAnswer(invocation -> {
            var table = invocation.getArgument(0, com.ael.algoryqrservice.model.RestaurantTable.class);
            table.setId(99L);
            return table;
        });

        service.assign(7L, "furkan");

        verify(menuThemeService).replaceThemes(7L, java.util.List.of("maison-noir"), null);
        verify(menuCatalogCloneService).cloneFromTemplate(created, 20L, 100L);
        verify(salesSeedService).seedFromTemplateOrSynthetic(20L, 40L, 99L, Map.of(1L, 2L), 7L, 35, 120);
        verify(assignmentRepository).save(any(DemoOnboardingAssignment.class));
    }

    @Test
    void assign_whenAlreadyAssigned_thenNoOp() {
        when(assignmentRepository.existsByUserId(7L)).thenReturn(true);

        service.assign(7L, "demo");

        verify(branchRepository, never()).save(any());
        verifyNoInteractions(qrService, menuThemeService);
    }
}
