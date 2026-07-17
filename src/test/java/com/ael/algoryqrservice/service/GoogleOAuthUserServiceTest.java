package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthUserServiceTest {

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PackageActivationService packageActivationService;

    @InjectMocks
    private GoogleOAuthUserService googleOAuthUserService;

    @Test
    void resolve_whenLoginAndGoogleUserExists_thenReturnUser() {
        User existing = googleUser("sub-1", "user@example.com");
        when(userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(existing));

        User result = googleOAuthUserService.resolve(
                GoogleAuthIntent.LOGIN,
                identity("sub-1", "user@example.com", true),
                clientInfo()
        );

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolve_whenLoginAndNoGoogleUser_thenThrowUnauthorized() {
        when(userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleOAuthUserService.resolve(
                GoogleAuthIntent.LOGIN,
                identity("sub-1", "user@example.com", true),
                clientInfo()
        ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google hesabı kayıtlı değil");
    }

    @Test
    void resolve_whenLoginAndBasicEmailExists_thenThrowProviderConflict() {
        when(userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(basicUser("user@example.com")));

        assertThatThrownBy(() -> googleOAuthUserService.resolve(
                GoogleAuthIntent.LOGIN,
                identity("sub-1", "user@example.com", true),
                clientInfo()
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("farklı bir giriş yöntemiyle");
    }

    @Test
    void resolve_whenRegisterAndEmailFree_thenCreateGoogleUser() {
        when(userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(NEXT_ID.incrementAndGet());
            return user;
        });

        User result = googleOAuthUserService.resolve(
                GoogleAuthIntent.REGISTER,
                identity("sub-1", "user@example.com", true),
                clientInfo()
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captor.getValue().getProviderSubject()).isEqualTo("sub-1");
        assertThat(captor.getValue().getPassword()).isNull();
        assertThat(result.getId()).isNotNull();
        verify(packageActivationService).ensureFreePackage(result.getId());
    }

    @Test
    void resolve_whenRegisterAndBasicEmailExists_thenThrowProviderConflict() {
        when(userRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(basicUser("user@example.com")));

        assertThatThrownBy(() -> googleOAuthUserService.resolve(
                GoogleAuthIntent.REGISTER,
                identity("sub-1", "user@example.com", true),
                clientInfo()
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("farklı bir giriş yöntemiyle");
    }

    @Test
    void resolve_whenEmailNotVerified_thenThrowUnauthorized() {
        assertThatThrownBy(() -> googleOAuthUserService.resolve(
                GoogleAuthIntent.LOGIN,
                identity("sub-1", "user@example.com", false),
                clientInfo()
        ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Doğrulanmış Google e-posta");
    }

    private static GoogleOidcIdentity identity(String subject, String email, boolean verified) {
        return new GoogleOidcIdentity(subject, email, "Ada", "Lovelace", verified);
    }

    private static ClientInfo clientInfo() {
        return new ClientInfo("127.0.0.1", "Mozilla", "Chrome", "DESKTOP");
    }

    private static User googleUser(String subject, String email) {
        return User.builder()
                .id(NEXT_ID.incrementAndGet())
                .firstName("Ada")
                .lastName("Lovelace")
                .email(email)
                .role(UserRole.USER)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(subject)
                .build();
    }

    private static User basicUser(String email) {
        return User.builder()
                .id(NEXT_ID.incrementAndGet())
                .firstName("Ada")
                .lastName("Lovelace")
                .email(email)
                .password("hash")
                .role(UserRole.USER)
                .provider(AuthProvider.BASIC)
                .build();
    }
}
