package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.product.service.ProductImageStorageService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuQrSoftDeleteService;
import com.ael.algoryqrservice.qrservice.order.service.KitchenCloseGuard;
import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.branch.dto.BranchDtos;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient fulfillmentClient;
    @Mock
    private com.ael.algoryqrservice.qrservice.security.ProductUsageGateway productUsageGateway;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private KitchenCloseGuard kitchenCloseGuard;

    @InjectMocks
    private BranchService branchService;

    @Test
    void delete_whenMenusExist_thenSoftDeletesMenusThenBranch() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").build();
        Menu menu = Menu.builder().menuId(4L).merchantId(7L).qrId(90L).branchId(15L).build();
        Qr qr = Qr.builder().qrId(90L).merchantId(7L).build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR)).thenReturn(List.of(menu));
        when(qrRepository.findById(90L)).thenReturn(Optional.of(qr));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        branchService.delete(15L);

        verify(menuQrSoftDeleteService).softDeleteMenuQr(qr);
        assertThat(branch.isDeleted()).isTrue();
        verify(branchRepository).save(branch);
    }

    @Test
    void delete_whenNoMenus_thenSoftDeletesBranchOnly() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR)).thenReturn(List.of());
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        branchService.delete(15L);

        verify(menuQrSoftDeleteService, never()).softDeleteMenuQr(any());
        assertThat(branch.isDeleted()).isTrue();
    }

    @Test
    void create_whenQuotaAllows_thenSavesBranch() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
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
        assertThat(response.isKitchenEnabled()).isFalse();
        verify(productUsageGateway).use(7L, com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts.QR_BRANCH, 1);
    }

    @Test
    void enableKitchen_whenOwned_thenTurnsKitchenOn() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").kitchenEnabled(false).build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR)).thenReturn(List.of());

        BranchDtos.Response response = branchService.enableKitchen(15L);

        assertThat(branch.isKitchenEnabled()).isTrue();
        assertThat(response.isKitchenEnabled()).isTrue();
    }

    @Test
    void update_whenClosingKitchenWithActiveOrders_thenRejects() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").kitchenEnabled(true).build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        doThrow(new BadRequestException("Aktif olan siparişleriniz var, mutfağı kapatamazsınız"))
                .when(kitchenCloseGuard).requireNoActiveOrders(branch);

        assertThatThrownBy(() -> branchService.update(15L, BranchDtos.UpdateRequest.builder()
                .kitchenEnabled(false)
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Aktif olan siparişleriniz var, mutfağı kapatamazsınız");
        assertThat(branch.isKitchenEnabled()).isTrue();
        verify(branchRepository, never()).save(any(Branch.class));
    }

    @Test
    void update_whenClosingKitchenWithoutActiveOrders_thenTurnsKitchenOff() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").kitchenEnabled(true).build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR)).thenReturn(List.of());

        branchService.update(15L, BranchDtos.UpdateRequest.builder().kitchenEnabled(false).build());

        verify(kitchenCloseGuard).requireNoActiveOrders(branch);
        assertThat(branch.isKitchenEnabled()).isFalse();
    }

    @Test
    void update_whenCourierEnabled_thenPersistsCourier() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch branch = Branch.builder().id(15L).merchantId(7L).name("Kadıköy").courierEnabled(false).build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(15L, 7L)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByBranchIdAndChannelAndDeletedFalse(15L, MenuChannel.QR)).thenReturn(List.of());

        BranchDtos.Response response = branchService.update(15L, BranchDtos.UpdateRequest.builder()
                .courierEnabled(true)
                .build());

        assertThat(branch.isCourierEnabled()).isTrue();
        assertThat(response.isCourierEnabled()).isTrue();
    }

    @Test
    void applyPhotoToAllBranches_whenSourceHasPhoto_thenCopies() {
        when(securityUtils.getCurrentMerchantId()).thenReturn(7L);
        Branch source = Branch.builder()
                .id(1L)
                .merchantId(7L)
                .name("A")
                .photoUrl("https://cdn/a.png")
                .photoKey("branches/1/photo/a.png")
                .build();
        Branch other = Branch.builder()
                .id(2L)
                .merchantId(7L)
                .name("B")
                .build();
        when(branchRepository.findByIdAndMerchantIdAndDeletedFalse(1L, 7L)).thenReturn(java.util.Optional.of(source));
        when(branchRepository.findByMerchantIdAndDeletedFalse(7L)).thenReturn(List.of(source, other));
        when(branchRepository.findByMerchantIdAndDeletedFalseOrderByIdDesc(7L)).thenReturn(List.of(source, other));
        when(menuRepository.findByMerchantIdAndChannelAndDeletedFalseOrderByMenuIdAsc(7L, MenuChannel.QR)).thenReturn(List.of());

        branchService.applyPhotoToAllBranches(1L);

        assertThat(other.getPhotoUrl()).isEqualTo("https://cdn/a.png");
        verify(branchRepository).save(other);
    }
}
