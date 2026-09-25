package com.ael.algoryqrservice.stage;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.BranchQuotaService;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.QrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageTrialFixtureServiceTest {

    @Mock
    private StageTrialAssignmentRepository assignmentRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private BranchQuotaService branchQuotaService;
    @Mock
    private QrService qrService;
    @Mock
    private MenuCatalogCloneService menuCatalogCloneService;

    private StageTrialFixtureProperties properties;
    private StageTrialFixtureService service;

    @BeforeEach
    void setUp() {
        properties = new StageTrialFixtureProperties();
        properties.setEnabled(true);
        properties.setTemplateUserId(1L);
        properties.setTemplateBranchId(10L);
        properties.setTemplateMenuId(20L);
        service = new StageTrialFixtureService(
                properties,
                assignmentRepository,
                branchRepository,
                menuRepository,
                branchQuotaService,
                qrService,
                menuCatalogCloneService
        );
    }

    @Test
    void assign_whenDisabled_thenDoesNothing() {
        properties.setEnabled(false);

        service.assign(7L);

        verifyNoInteractions(assignmentRepository, branchRepository, menuRepository, qrService, menuCatalogCloneService);
    }

    @Test
    void assign_whenAlreadyAssigned_thenDoesNotCopy() {
        when(assignmentRepository.existsByUserId(7L)).thenReturn(true);

        service.assign(7L);

        verify(branchRepository, never()).save(any());
        verifyNoInteractions(qrService, menuCatalogCloneService);
    }

    @Test
    void assign_whenReady_thenCopiesBranchMenuAndProducts() throws Exception {
        Branch templateBranch = Branch.builder()
                .id(10L)
                .userId(1L)
                .name("Merkez")
                .address("Cadde")
                .phone("555")
                .email("cafe@example.com")
                .photoUrl("https://cdn.example/branch.jpg")
                .photoKey("branch.jpg")
                .build();
        Menu templateMenu = Menu.builder()
                .menuId(20L)
                .userId(1L)
                .branchId(10L)
                .themeId("classic")
                .businessName("Kafe")
                .logoUrl("https://cdn.example/logo.jpg")
                .coverUrl("https://cdn.example/cover.jpg")
                .active(true)
                .deleted(false)
                .build();
        Menu created = Menu.builder().menuId(40L).userId(7L).branchId(30L).build();
        when(assignmentRepository.existsByUserId(7L)).thenReturn(false);
        when(branchRepository.findByIdAndUserIdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(templateBranch));
        when(menuRepository.findById(20L)).thenReturn(Optional.of(templateMenu));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(30L);
            return branch;
        });
        when(qrService.createQR(any(QrRequest.class), eq(7L)))
                .thenReturn(QrResponse.builder().menuId(40L).build());
        when(menuRepository.findById(40L)).thenReturn(Optional.of(created));

        service.assign(7L);

        ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(branchCaptor.capture());
        assertThat(branchCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(branchCaptor.getValue().getName()).isEqualTo("Merkez");
        assertThat(branchCaptor.getValue().getPhotoUrl()).isEqualTo("https://cdn.example/branch.jpg");
        verify(menuCatalogCloneService).cloneFromTemplate(created, 20L, 1L);
        assertThat(created.getLogoUrl()).isEqualTo("https://cdn.example/logo.jpg");
        assertThat(created.getCoverUrl()).isEqualTo("https://cdn.example/cover.jpg");
        ArgumentCaptor<StageTrialAssignment> assignmentCaptor = ArgumentCaptor.forClass(StageTrialAssignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(assignmentCaptor.getValue().getBranchId()).isEqualTo(30L);
        assertThat(assignmentCaptor.getValue().getMenuId()).isEqualTo(40L);
    }
}
