package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.platform.service.NotificationPublisherService;
import com.ael.algoryqrservice.adminservice.application.AdminUserCredentialService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.adminservice.dto.AdminUserDtos;
import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.client.AuthCredentialClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    NotificationPublisherService notificationPublisherService;
    @Mock
    AuthCredentialClient authCredentialClient;

    AdminUserCredentialService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserCredentialService(
                userRepository,
                passwordEncoder,
                notificationPublisherService,
                authCredentialClient
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

        verify(authCredentialClient).sendEmailCode(7L);
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
