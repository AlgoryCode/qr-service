package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
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
    private MenuWaiterRepository menuWaiterRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private WaiterAccessService waiterAccessService;

    @Test
    void requireWaiterForMenu_whenMenuOnSameBranch_thenReturnsWaiter() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(menuWaiterRepository.findById(7L)).thenReturn(Optional.of(waiter));
        when(menuRepository.findById(12L)).thenReturn(Optional.of(Menu.builder()
                .menuId(12L)
                .branchId(4L)
                .deleted(false)
                .build()));

        MenuWaiter result = waiterAccessService.requireWaiterForMenu(12L);

        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    void requireWaiterForMenu_whenMenuOnOtherBranch_thenForbidden() {
        MenuWaiter waiter = MenuWaiter.builder()
                .id(7L)
                .branchId(4L)
                .active(true)
                .build();
        when(securityUtils.getCurrentWaiterId()).thenReturn(7L);
        when(securityUtils.getCurrentWaiterBranchId()).thenReturn(4L);
        when(menuWaiterRepository.findById(7L)).thenReturn(Optional.of(waiter));
        when(menuRepository.findById(12L)).thenReturn(Optional.of(Menu.builder()
                .menuId(12L)
                .branchId(99L)
                .deleted(false)
                .build()));

        assertThatThrownBy(() -> waiterAccessService.requireWaiterForMenu(12L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("şubeye erişim");
    }
}
