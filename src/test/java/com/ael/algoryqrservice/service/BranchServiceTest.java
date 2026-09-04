package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private QrRepository qrRepository;
    @Mock
    private MenuQrSoftDeleteService menuQrSoftDeleteService;
    @Mock
    private BranchQuotaService branchQuotaService;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private BranchService branchService;

    @Test
    void delete_whenMenusExist_thenSoftDeletesMenusThenBranch() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).userId(7L).name("Kadıköy").build();
        Menu menu = Menu.builder().menuId(4L).userId(7L).qrId(90L).branchId(15L).build();
        Qr qr = Qr.builder().qrId(90L).userId(7L).build();
        when(branchRepository.findByIdAndUserIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(menuRepository.findByBranchIdAndDeletedFalse(15L)).thenReturn(List.of(menu));
        when(qrRepository.findById(90L)).thenReturn(Optional.of(qr));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        branchService.delete(15L);

        verify(menuQrSoftDeleteService).softDeleteMenuQr(qr);
        assertThat(branch.isDeleted()).isTrue();
        verify(branchRepository).save(branch);
    }

    @Test
    void delete_whenNoMenus_thenSoftDeletesBranchOnly() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).userId(7L).name("Kadıköy").build();
        when(branchRepository.findByIdAndUserIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(menuRepository.findByBranchIdAndDeletedFalse(15L)).thenReturn(List.of());
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        branchService.delete(15L);

        verify(menuQrSoftDeleteService, never()).softDeleteMenuQr(any());
        assertThat(branch.isDeleted()).isTrue();
    }

    @Test
    void create_whenQuotaAllows_thenSavesBranch() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(15L);
            return branch;
        });

        BranchDtos.Response response = branchService.create(BranchDtos.CreateRequest.builder()
                .name("Kadıköy")
                .address("Moda")
                .phone("02160000000")
                .email("k@example.com")
                .build());

        assertThat(response.getId()).isEqualTo(15L);
        assertThat(response.getName()).isEqualTo("Kadıköy");
        verify(branchQuotaService).assertCanCreateBranch(7L);
    }

    @Test
    void backfillMissingBranches_whenMenuHasNoBranch_thenCreatesGrandfatheredBranch() {
        Menu menu = Menu.builder()
                .menuId(4L)
                .userId(7L)
                .businessName("Cafe Ada")
                .logoUrl("https://cdn/logo.png")
                .logoKey("menus/4/logo/a.png")
                .phone("0216")
                .address("Ada")
                .active(true)
                .build();
        when(menuRepository.findAll()).thenReturn(List.of(menu));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(22L);
            return branch;
        });

        int created = branchService.backfillMissingBranches();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(menuCaptor.capture());
        assertThat(menuCaptor.getValue().getBranchId()).isEqualTo(22L);
        ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(branchCaptor.capture());
        assertThat(branchCaptor.getValue().getName()).isEqualTo("Cafe Ada");
        assertThat(branchCaptor.getValue().isGrandfathered()).isTrue();
        assertThat(branchCaptor.getValue().getPhotoUrl()).isEqualTo("https://cdn/logo.png");
    }

    @Test
    void applyPhotoToAllBranches_whenSourceHasPhoto_thenCopies() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        Branch source = Branch.builder()
                .id(1L)
                .userId(7L)
                .name("A")
                .photoUrl("https://cdn/a.png")
                .photoKey("branches/1/photo/a.png")
                .build();
        Branch other = Branch.builder()
                .id(2L)
                .userId(7L)
                .name("B")
                .build();
        when(branchRepository.findByIdAndUserIdAndDeletedFalse(1L, 7L)).thenReturn(java.util.Optional.of(source));
        when(branchRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(List.of(source, other));
        when(branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(7L)).thenReturn(List.of(source, other));
        when(menuRepository.findByUserIdAndDeletedFalseOrderByMenuIdAsc(7L)).thenReturn(List.of());
        when(branchQuotaService.branchQuota(7L)).thenReturn(BranchDtos.Quota.builder().canCreate(false).build());
        when(branchQuotaService.menuQuota(7L)).thenReturn(BranchDtos.MenuQuota.builder().build());

        branchService.applyPhotoToAllBranches(1L);

        assertThat(other.getPhotoUrl()).isEqualTo("https://cdn/a.png");
        verify(branchRepository).save(other);
    }
}
