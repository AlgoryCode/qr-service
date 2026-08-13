package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuReservation;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.enums.MenuReservationStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuReservationRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuReservationServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuReservationRepository menuReservationRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MenuReservationService menuReservationService;

    @Test
    void createPublic_whenPhoneOnly_thenPending() {
        when(menuRepository.findById(10L)).thenReturn(Optional.of(publicMenu()));
        when(menuReservationRepository.countByMenuIdAndIpAddressAndCreatedAtAfter(eq(10L), eq("1.2.3.4"), any()))
                .thenReturn(0L);
        when(menuReservationRepository.save(any(MenuReservation.class)))
                .thenAnswer(invocation -> {
                    MenuReservation saved = invocation.getArgument(0);
                    saved.setId(55L);
                    return saved;
                });

        MenuDtos.ReservationResponse response = menuReservationService.createPublic(
                10L,
                MenuDtos.ReservationCreateRequest.builder()
                        .customerName("Ayşe Yılmaz")
                        .phone("05551234567")
                        .partySize(3)
                        .reservationAt(LocalDateTime.now().plusDays(1))
                        .build(),
                "1.2.3.4",
                "Mozilla/5.0"
        );

        assertThat(response.getId()).isEqualTo(55L);
        assertThat(response.getStatus()).isEqualTo(MenuReservationStatus.PENDING);
        assertThat(response.getPhone()).isEqualTo("05551234567");
        assertThat(response.getEmail()).isNull();

        ArgumentCaptor<MenuReservation> captor = ArgumentCaptor.forClass(MenuReservation.class);
        verify(menuReservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MenuReservationStatus.PENDING);
    }

    @Test
    void createPublic_whenEmailOnly_thenPending() {
        when(menuRepository.findById(10L)).thenReturn(Optional.of(publicMenu()));
        when(menuReservationRepository.countByMenuIdAndIpAddressAndCreatedAtAfter(eq(10L), eq("1.2.3.4"), any()))
                .thenReturn(1L);
        when(menuReservationRepository.save(any(MenuReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.ReservationResponse response = menuReservationService.createPublic(
                10L,
                MenuDtos.ReservationCreateRequest.builder()
                        .customerName("Ali")
                        .email("Ali@Example.com")
                        .partySize(2)
                        .reservationAt(LocalDateTime.now().plusHours(5))
                        .build(),
                "1.2.3.4",
                null
        );

        assertThat(response.getEmail()).isEqualTo("ali@example.com");
        assertThat(response.getStatus()).isEqualTo(MenuReservationStatus.PENDING);
    }

    @Test
    void createPublic_whenNoContact_thenBadRequest() {
        when(menuRepository.findById(10L)).thenReturn(Optional.of(publicMenu()));

        assertThatThrownBy(() -> menuReservationService.createPublic(
                10L,
                MenuDtos.ReservationCreateRequest.builder()
                        .customerName("Ali")
                        .partySize(2)
                        .reservationAt(LocalDateTime.now().plusDays(1))
                        .build(),
                "1.1.1.1",
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createPublic_whenRateLimited_thenTooManyRequests() {
        when(menuRepository.findById(10L)).thenReturn(Optional.of(publicMenu()));
        when(menuReservationRepository.countByMenuIdAndIpAddressAndCreatedAtAfter(eq(10L), eq("9.9.9.9"), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> menuReservationService.createPublic(
                10L,
                MenuDtos.ReservationCreateRequest.builder()
                        .customerName("Ali")
                        .phone("0555")
                        .partySize(2)
                        .reservationAt(LocalDateTime.now().plusDays(1))
                        .build(),
                "9.9.9.9",
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void updateForOwner_whenPendingToActive_thenOk() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(menuRepository.findById(10L)).thenReturn(Optional.of(ownedMenu()));
        MenuReservation existing = reservation(1L, MenuReservationStatus.PENDING);
        when(menuReservationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuReservationRepository.save(any(MenuReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MenuDtos.ReservationResponse response = menuReservationService.updateForOwner(
                10L,
                1L,
                MenuDtos.ReservationUpdateRequest.builder().status(MenuReservationStatus.ACTIVE).build()
        );

        assertThat(response.getStatus()).isEqualTo(MenuReservationStatus.ACTIVE);
        assertThat(existing.getStatus()).isEqualTo(MenuReservationStatus.ACTIVE);
    }

    @Test
    void updateForOwner_whenEditReservationAt_thenOk() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(menuRepository.findById(10L)).thenReturn(Optional.of(ownedMenu()));
        MenuReservation existing = reservation(1L, MenuReservationStatus.ACTIVE);
        when(menuReservationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuReservationRepository.save(any(MenuReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime next = LocalDateTime.of(2026, 9, 1, 20, 0);
        MenuDtos.ReservationResponse response = menuReservationService.updateForOwner(
                10L,
                1L,
                MenuDtos.ReservationUpdateRequest.builder().reservationAt(next).build()
        );

        assertThat(response.getReservationAt()).isEqualTo(next);
    }

    @Test
    void listForOwner_whenQueryProvided_thenDelegates() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(menuRepository.findById(10L)).thenReturn(Optional.of(ownedMenu()));
        when(menuReservationRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reservation(3L, MenuReservationStatus.PENDING))));

        MenuDtos.ReservationPageResponse page = menuReservationService.listForOwner(
                10L,
                "PENDING",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "ayse",
                0,
                20
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getCustomerName()).isEqualTo("Ayşe Yılmaz");
        verify(menuReservationRepository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void updateForOwner_whenNotOwner_thenForbidden() {
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(menuRepository.findById(10L)).thenReturn(Optional.of(ownedMenu()));

        assertThatThrownBy(() -> menuReservationService.updateForOwner(
                10L,
                1L,
                MenuDtos.ReservationUpdateRequest.builder().status(MenuReservationStatus.ACTIVE).build()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Menu publicMenu() {
        return Menu.builder()
                .menuId(10L)
                .userId(9L)
                .qrId(1L)
                .themeId("soft")
                .businessName("Test")
                .active(true)
                .publicAccessEnabled(true)
                .build();
    }

    private Menu ownedMenu() {
        return publicMenu();
    }

    private MenuReservation reservation(Long id, MenuReservationStatus status) {
        return MenuReservation.builder()
                .id(id)
                .menuId(10L)
                .customerName("Ayşe Yılmaz")
                .phone("05551234567")
                .partySize(2)
                .reservationAt(LocalDateTime.now().plusDays(1))
                .status(status)
                .ipAddress("1.2.3.4")
                .deviceType("MOBILE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
