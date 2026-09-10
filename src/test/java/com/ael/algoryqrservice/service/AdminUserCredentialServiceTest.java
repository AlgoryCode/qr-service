package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserCredentialServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    SessionService sessionService;
    @Mock
    NotificationPublisherService notificationPublisherService;
    @Mock
    EmailVerificationService emailVerificationService;

    AdminUserCredentialService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserCredentialService(
                userRepository,
                passwordEncoder,
                sessionService,
                notificationPublisherService,
                emailVerificationService
        );
    }

    @Test
    void resetPassword_whenVerified_thenEmailAndReturnPassword() {
        User user = basicUser(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        AdminUserDtos.PasswordResetResponse result = service.resetPassword(7L);

        assertThat(result.getTemporaryPassword()).hasSize(12);
        assertThat(result.isEmailed()).isTrue();
        assertThat(user.getPassword()).isEqualTo("hashed");
        verify(sessionService).revokeAllActiveSessions(7L);
        verify(notificationPublisherService).publishTemporaryPassword(
                eq("user@example.com"),
                eq("Ada"),
                eq(result.getTemporaryPassword())
        );
    }

    @Test
    void resetPassword_whenUnverified_thenSkipMail() {
        User user = basicUser(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        AdminUserDtos.PasswordResetResponse result = service.resetPassword(7L);

        assertThat(result.getTemporaryPassword()).isNotBlank();
        assertThat(result.isEmailed()).isFalse();
        verify(notificationPublisherService, never()).publishTemporaryPassword(anyString(), anyString(), anyString());
        verify(sessionService).revokeAllActiveSessions(7L);
    }

    @Test
    void resetPassword_whenMailFails_thenStillReturnPassword() {
        User user = basicUser(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        doThrow(new RuntimeException("queue down"))
                .when(notificationPublisherService)
                .publishTemporaryPassword(anyString(), anyString(), anyString());

        AdminUserDtos.PasswordResetResponse result = service.resetPassword(7L);

        assertThat(result.isEmailed()).isFalse();
        assertThat(result.getTemporaryPassword()).isNotBlank();
    }

    @Test
    void resetPassword_whenGoogle_thenReject() {
        User user = User.builder()
                .id(7L)
                .firstName("Ada")
                .email("user@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Google");
        verify(sessionService, never()).revokeAllActiveSessions(7L);
    }

    @Test
    void resetPassword_whenMissing_thenNotFound() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(7L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendEmailVerification_whenExists_thenDelegate() {
        User user = basicUser(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.sendEmailVerification(7L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(emailVerificationService).sendForAdmin(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }

    private static User basicUser(boolean verified) {
        return User.builder()
                .id(7L)
                .firstName("Ada")
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .emailVerified(verified)
                .build();
    }
}
