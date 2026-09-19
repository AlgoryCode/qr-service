package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.MenuPublicIdGenerator;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantSetupServiceTest {

    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MenuPublicIdGenerator menuPublicIdGenerator;
    @Mock
    private MenuCatalogCloneService menuCatalogCloneService;
    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private StoreSlugGenerator storeSlugGenerator;
    @Mock
    private StoreTokenGenerator storeTokenGenerator;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MerchantSetupService merchantSetupService;

    @Test
    void prefill_whenUserHasBranchMenus_thenListsThoseMenus() {
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        Branch kadikoy = Branch.builder().id(15L).userId(7L).name("Kadıköy").build();
        Branch besiktas = Branch.builder().id(16L).userId(7L).name("Beşiktaş").build();
        Menu kadikoyMenu = Menu.builder()
                .menuId(4L)
                .userId(7L)
                .branchId(15L)
                .channel(MenuChannel.QR)
                .businessName("Kebapçı")
                .active(true)
                .build();
        Menu storeMenu = Menu.builder()
                .menuId(9L)
                .userId(7L)
                .channel(MenuChannel.STORE)
                .businessName("Paket")
                .active(true)
                .build();
        when(branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(7L)).thenReturn(List.of(kadikoy, besiktas));
        when(menuRepository.findActiveMenusWithQrByUserId(7L)).thenReturn(List.of(
                new Object[]{kadikoyMenu, Qr.builder().qrId(90L).build()},
                new Object[]{storeMenu, Qr.builder().qrId(91L).build()}
        ));
        when(merchantRepository.existsByUserIdAndDeletedFalse(7L)).thenReturn(false);
        when(menuProductRepository.countByMenuIdAndDeletedFalse(4L)).thenReturn(12L);

        StoreDtos.SetupPrefillResponse response = merchantSetupService.prefill();

        assertThat(response.alreadySetUp()).isFalse();
        assertThat(response.menus()).hasSize(1);
        assertThat(response.menus().getFirst().menuId()).isEqualTo(4L);
        assertThat(response.menus().getFirst().businessName()).isEqualTo("Kebapçı");
        assertThat(response.menus().getFirst().productCount()).isEqualTo(12);
        assertThat(response.menus().getFirst().branchName()).isEqualTo("Kadıköy");
    }
}
