package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuWaiterServiceTest {

    @Mock
    private MenuWaiterRepository menuWaiterRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BranchService branchService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MenuWaiterService menuWaiterService;

    @Test
    void createWaiter_whenBranchOwned_thenPersistsBranchId() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(branchService.requireOwnedForUser(4L, 9L)).thenReturn(Branch.builder()
                .id(4L)
                .userId(9L)
                .name("Kadikoy")
                .build());
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(menuWaiterRepository.existsByUsernameIgnoreCase("ali")).thenReturn(false);
        when(menuWaiterRepository.save(any(MenuWaiter.class))).thenAnswer(invocation -> {
            MenuWaiter waiter = invocation.getArgument(0);
            waiter.setId(11L);
            return waiter;
        });

        MenuWaiterDtos.WaiterResponse response = menuWaiterService.createWaiter(
                4L,
                MenuWaiterDtos.CreateWaiterRequest.builder()
                        .username("Ali")
                        .password("secret1")
                        .displayName("Ali Garson")
                        .build()
        );

        ArgumentCaptor<MenuWaiter> captor = ArgumentCaptor.forClass(MenuWaiter.class);
        verify(menuWaiterRepository).save(captor.capture());
        assertThat(captor.getValue().getBranchId()).isEqualTo(4L);
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(9L);
        assertThat(captor.getValue().getUsername()).isEqualTo("ali");
        assertThat(captor.getValue().getStaffRole()).isEqualTo(com.ael.algoryqrservice.model.enums.StaffRole.WAITER);
        assertThat(response.getBranchId()).isEqualTo(4L);
        assertThat(response.getDisplayName()).isEqualTo("Ali Garson");
    }

    @Test
    void createWaiter_whenKitchenRole_thenPersistsKitchen() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(branchService.requireOwnedForUser(4L, 9L)).thenReturn(Branch.builder()
                .id(4L)
                .userId(9L)
                .name("Kadikoy")
                .kitchenEnabled(true)
                .build());
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(menuWaiterRepository.existsByUsernameIgnoreCase("mutfak1")).thenReturn(false);
        when(menuWaiterRepository.save(any(MenuWaiter.class))).thenAnswer(invocation -> {
            MenuWaiter waiter = invocation.getArgument(0);
            waiter.setId(12L);
            return waiter;
        });

        MenuWaiterDtos.WaiterResponse response = menuWaiterService.createWaiter(
                4L,
                MenuWaiterDtos.CreateWaiterRequest.builder()
                        .username("mutfak1")
                        .password("secret1")
                        .displayName("Mutfak")
                        .staffRole(com.ael.algoryqrservice.model.enums.StaffRole.KITCHEN)
                        .build()
        );

        ArgumentCaptor<MenuWaiter> captor = ArgumentCaptor.forClass(MenuWaiter.class);
        verify(menuWaiterRepository).save(captor.capture());
        assertThat(captor.getValue().getStaffRole()).isEqualTo(com.ael.algoryqrservice.model.enums.StaffRole.KITCHEN);
        assertThat(response.getStaffRole()).isEqualTo(com.ael.algoryqrservice.model.enums.StaffRole.KITCHEN);
    }

    @Test
    void createWaiter_whenCourierRole_thenPersistsCourier() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(branchService.requireOwnedForUser(4L, 9L)).thenReturn(Branch.builder()
                .id(4L)
                .userId(9L)
                .name("Kadikoy")
                .build());
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(menuWaiterRepository.existsByUsernameIgnoreCase("kurye1")).thenReturn(false);
        when(menuWaiterRepository.save(any(MenuWaiter.class))).thenAnswer(invocation -> {
            MenuWaiter waiter = invocation.getArgument(0);
            waiter.setId(13L);
            return waiter;
        });

        MenuWaiterDtos.WaiterResponse response = menuWaiterService.createWaiter(
                4L,
                MenuWaiterDtos.CreateWaiterRequest.builder()
                        .username("kurye1")
                        .password("secret1")
                        .displayName("Ali Kurye")
                        .staffRole(com.ael.algoryqrservice.model.enums.StaffRole.COURIER)
                        .build()
        );

        ArgumentCaptor<MenuWaiter> captor = ArgumentCaptor.forClass(MenuWaiter.class);
        verify(menuWaiterRepository).save(captor.capture());
        assertThat(captor.getValue().getStaffRole()).isEqualTo(com.ael.algoryqrservice.model.enums.StaffRole.COURIER);
        assertThat(response.getStaffRole()).isEqualTo(com.ael.algoryqrservice.model.enums.StaffRole.COURIER);
    }

    @Test
    void createWaiter_whenKitchenRoleWithoutKitchen_thenRejects() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(branchService.requireOwnedForUser(4L, 9L)).thenReturn(Branch.builder()
                .id(4L)
                .userId(9L)
                .name("Kadikoy")
                .kitchenEnabled(false)
                .build());

        assertThatThrownBy(() -> menuWaiterService.createWaiter(
                4L,
                MenuWaiterDtos.CreateWaiterRequest.builder()
                        .username("mutfak1")
                        .password("secret1")
                        .displayName("Mutfak")
                        .staffRole(com.ael.algoryqrservice.model.enums.StaffRole.KITCHEN)
                        .build()
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("şube ayarlarından");
    }

    @Test
    void listWaiters_whenBranchOwned_thenReturnsWaiters() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(branchService.requireOwnedForUser(4L, 9L)).thenReturn(Branch.builder()
                .id(4L)
                .userId(9L)
                .name("Kadikoy")
                .build());
        when(userRepository.findById(9L)).thenReturn(Optional.of(User.builder()
                .id(9L)
                .firstName("Ada")
                .lastName("Cafe")
                .email("ada@example.com")
                .build()));
        when(menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(4L)).thenReturn(List.of(
                MenuWaiter.builder()
                        .id(11L)
                        .branchId(4L)
                        .username("ali")
                        .displayName("Ali")
                        .active(true)
                        .build()
        ));

        MenuWaiterDtos.UsersPageResponse page = menuWaiterService.listWaiters(4L);

        assertThat(page.getOwner().getEmail()).isEqualTo("ada@example.com");
        assertThat(page.getWaiters()).hasSize(1);
        assertThat(page.getWaiters().getFirst().getBranchId()).isEqualTo(4L);
    }
}
