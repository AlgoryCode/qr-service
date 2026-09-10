package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.trial.TrialUseCases;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    UserAccessProfileService userAccessProfileService;
    @Mock
    PurchaseService purchaseService;
    @Mock
    QrRepository qrRepository;
    @Mock
    MenuRepository menuRepository;
    @Mock
    SessionService sessionService;
    @Mock
    TrialUseCases trialUseCases;
    @Mock
    EmailVerificationService emailVerificationService;

    AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                userAccessProfileService,
                purchaseService,
                qrRepository,
                menuRepository,
                sessionService,
                trialUseCases,
                emailVerificationService
        );
    }

    @Test
    void updateUser_whenEmailChanges_thenUnverifyAndSendMail() {
        User user = basicUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("yeni@example.com", 7L)).thenReturn(false);
        stubDetail(user);

        AdminUserDtos.UserDetailResponse result = service.updateUser(7L, updateRequest("yeni@example.com"));

        assertThat(user.getEmail()).isEqualTo("yeni@example.com");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(result.getEmailVerified()).isFalse();
        verify(emailVerificationService).sendForUser(user);
    }

    @Test
    void updateUser_whenEmailUnchanged_thenSkipVerificationMail() {
        User user = basicUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        stubDetail(user);

        service.updateUser(7L, updateRequest("user@example.com"));

        verify(emailVerificationService, never()).sendForUser(user);
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void updateUser_whenGoogleEmailChange_thenReject() {
        User user = User.builder()
                .id(7L)
                .firstName("Ada")
                .email("user@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUser(7L, updateRequest("yeni@example.com")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Google");
        verify(emailVerificationService, never()).sendForUser(user);
    }

    @Test
    void getUserById_whenGoogle_thenEmailVerifiedNull() {
        User user = User.builder()
                .id(7L)
                .firstName("Ada")
                .email("user@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        stubDetail(user);

        AdminUserDtos.UserDetailResponse result = service.getUserById(7L);

        assertThat(result.getEmailVerified()).isNull();
    }

    private void stubDetail(User user) {
        when(userAccessProfileService.resolve(7L)).thenReturn(new UserAccessProfile(null, List.of(), List.of()));
        when(purchaseService.getUserPurchases(7L)).thenReturn(List.of());
        when(trialUseCases.snapshot(7L)).thenReturn(TrialSnapshot.neverStarted());
        when(qrRepository.countByUserIdAndDeletedFalse(7L)).thenReturn(0L);
        when(menuRepository.countActiveLiveMenusForUser(7L)).thenReturn(0L);
    }

    private static User basicUser() {
        return User.builder()
                .id(7L)
                .firstName("Ada")
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .emailVerified(true)
                .build();
    }

    private static AdminUserDtos.UserUpdateRequest updateRequest(String email) {
        return AdminUserDtos.UserUpdateRequest.builder()
                .firstName("Ada")
                .email(email)
                .build();
    }
}
