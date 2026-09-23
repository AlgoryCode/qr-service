package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterAccessServiceTest {

    @Mock
    private MerchantStaffRepository merchantStaffRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private WaiterAccessService waiterAccessService;

    @Test
    void requireWaiterForMenu_whenMenuOnSameBranch_thenReturnsWaiter() {
        MerchantStaff waiter = MerchantStaff.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(7L)).thenReturn(Optional.of(waiter));
        when(menuRepository.findById(12L)).thenReturn(Optional.of(Menu.builder()
                .menuId(12L)
                .branchId(4L)
                .deleted(false)
                .build()));

        MerchantStaff result = waiterAccessService.requireWaiterForMenu(12L);

        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    void requireWaiterForMenu_whenMenuOnOtherBranch_thenForbidden() {
        MerchantStaff waiter = MerchantStaff.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(7L)).thenReturn(Optional.of(waiter));
        when(menuRepository.findById(12L)).thenReturn(Optional.of(Menu.builder()
                .menuId(12L)
                .branchId(99L)
                .deleted(false)
                .build()));

        assertThatThrownBy(() -> waiterAccessService.requireWaiterForMenu(12L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("şubeye erişim");
    }

    @Test
    void requireKitchenStaff_whenWaiterRole_thenForbidden() {
        MerchantStaff waiter = MerchantStaff.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .staffRole(StaffRole.WAITER)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(7L)).thenReturn(Optional.of(waiter));

        assertThatThrownBy(() -> waiterAccessService.requireKitchenStaff())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("mutfak");
    }

    @Test
    void requireWaiterStaff_whenKitchenRole_thenForbidden() {
        MerchantStaff staff = MerchantStaff.builder()
                .id(8L)
                .branchId(4L)
                .active(true)
                .staffRole(StaffRole.KITCHEN)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(8L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(8L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> waiterAccessService.requireWaiterStaff())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("garson");
    }

    @Test
    void requireWaiterStaff_whenCourierRole_thenForbidden() {
        MerchantStaff staff = MerchantStaff.builder()
                .id(9L)
                .branchId(4L)
                .active(true)
                .staffRole(StaffRole.COURIER)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(9L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(9L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> waiterAccessService.requireWaiterStaff())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("garson");
    }

    @Test
    void requireCourierStaff_whenWaiterRole_thenForbidden() {
        MerchantStaff waiter = MerchantStaff.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .staffRole(StaffRole.WAITER)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(merchantStaffRepository.findById(7L)).thenReturn(Optional.of(waiter));

        assertThatThrownBy(() -> waiterAccessService.requireCourierStaff())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("kurye");
    }
}
