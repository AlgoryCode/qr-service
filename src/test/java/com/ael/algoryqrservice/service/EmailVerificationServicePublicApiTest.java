package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServicePublicApiTest {

    @Mock
    SecurityUtils securityUtils;
    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    NotificationPublisherService notificationPublisherService;

    EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                securityUtils,
                userRepository,
                passwordEncoder,
                notificationPublisherService
        );
        ReflectionTestUtils.setField(service, "codeValidityMinutes", 15);
    }

    @Test
    void resendByEmail_whenUnknown_thenNoOp() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.resendByEmail(new EmailVerificationDtos.ResendByEmailRequest("missing@example.com"));

        verify(notificationPublisherService, never()).publishEmailVerificationCode(
                anyString(), anyString(), anyString(), anyInt()
        );
    }

    @Test
    void resendByEmail_whenUnverifiedBasic_thenSendCode() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .firstName("Ada")
                .lastName("Lovelace")
                .provider(AuthProvider.BASIC)
                .emailVerified(false)
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        service.resendByEmail(new EmailVerificationDtos.ResendByEmailRequest("user@example.com"));

        verify(notificationPublisherService).publishEmailVerificationCode(
                eq("user@example.com"),
                eq("Ada Lovelace"),
                anyString(),
                eq(15)
        );
        assertThat(user.getEmailVerificationCodeHash()).isEqualTo("hash");
    }

    @Test
    void verifyByEmail_whenCodeValid_thenMarkVerified() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .emailVerified(false)
                .emailVerificationCodeHash("hash")
                .emailVerificationExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        EmailVerificationDtos.Status status = service.verifyByEmail(
                new EmailVerificationDtos.PublicVerifyRequest("user@example.com", "123456")
        );

        assertThat(status.verified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationCodeHash()).isNull();
    }

    @Test
    void verifyByEmail_whenCodeInvalid_thenBadRequest() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .provider(AuthProvider.BASIC)
                .emailVerified(false)
                .emailVerificationCodeHash("hash")
                .emailVerificationExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyByEmail(
                new EmailVerificationDtos.PublicVerifyRequest("user@example.com", "000000")
        )).isInstanceOf(BadRequestException.class);
    }
}
