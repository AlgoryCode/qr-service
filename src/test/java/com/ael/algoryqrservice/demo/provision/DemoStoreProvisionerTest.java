package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.demoonboarding.DemoOnboardingAssignment;
import com.ael.algoryqrservice.demoonboarding.DemoOnboardingAssignmentRepository;
import com.ael.algoryqrservice.demoonboarding.DemoOnboardingSalesSeedService;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.BranchQuotaService;
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.service.QrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoStoreProvisionerTest {

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
    private MenuThemeService menuThemeService;
    @Mock
    private DemoOnboardingSalesSeedService salesSeedService;
    @Mock
    private DemoThemePicker themePicker;
    @Mock
    private DemoProductCatalogSeeder productCatalogSeeder;

    private DemoTemplateProperties template;
    private DemoStoreProvisioner provisioner;

    @BeforeEach
    void setUp() {
        template = new DemoTemplateProperties();
        template.setEnabled(true);
        DemoMenuPresentationFixture presentationFixture = new DemoMenuPresentationFixture(new ObjectMapper());
        provisioner = new DemoStoreProvisioner(
                template,
                presentationFixture,
                themePicker,
                productCatalogSeeder,
                assignmentRepository,
                branchRepository,
                menuRepository,
                restaurantTableRepository,
                branchQuotaService,
                qrService,
                menuThemeService,
                salesSeedService
        );
    }

    @Test
    void provisionStore_whenDisabled_thenSkips() {
        template.setEnabled(false);

        provisioner.provisionStore(7L, "demo");

        verifyNoInteractions(assignmentRepository, menuThemeService, qrService);
    }

    @Test
    void provisionStore_whenReady_thenRunsPipelineSteps() throws Exception {
        Menu created = Menu.builder().menuId(40L).userId(7L).branchId(11L).build();

        when(themePicker.pickThemeCode(any())).thenReturn("maison-noir");
        when(assignmentRepository.existsByUserId(7L)).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(11L);
            return branch;
        });
        when(qrService.createQR(any(QrRequest.class), eq(7L)))
                .thenReturn(QrResponse.builder().menuId(40L).build());
        when(menuRepository.findById(40L)).thenReturn(Optional.of(created));
        when(productCatalogSeeder.seedCatalog(40L, 7L)).thenReturn(13);
        when(restaurantTableRepository.findFirstByMenuIdAndActiveTrueAndDeletedFalseOrderByTableNumberAscNameAsc(40L))
                .thenReturn(Optional.empty());
        when(restaurantTableRepository.save(any())).thenAnswer(invocation -> {
            var table = invocation.getArgument(0, com.ael.algoryqrservice.model.RestaurantTable.class);
            table.setId(99L);
            return table;
        });

        provisioner.provisionStore(7L, "furkan");

        verify(menuThemeService, org.mockito.Mockito.atLeastOnce())
                .replaceThemes(7L, List.of("maison-noir"), null);
        verify(productCatalogSeeder).seedCatalog(40L, 7L);
        verify(salesSeedService).seedSyntheticSales(40L, 99L, 7L, 35, 120);
        verify(assignmentRepository).save(any(DemoOnboardingAssignment.class));
    }

    @Test
    void provisionStore_whenAlreadyAssigned_thenNoOp() {
        when(assignmentRepository.existsByUserId(7L)).thenReturn(true);

        provisioner.provisionStore(7L, "demo");

        verify(branchRepository, never()).save(any());
        verifyNoInteractions(qrService, menuThemeService);
    }
}
