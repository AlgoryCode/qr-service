package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthUserServiceTest {

    private static final ClientInfo CLIENT_INFO = new ClientInfo(
            "127.0.0.1",
            "test-agent",
            "test-device",
            "browser"
    );

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPackageService userPackageService;
    @InjectMocks
    private GoogleOAuthUserService service;

    @Test
    void resolve_whenRegisterIntentIsValid_thenCreateGoogleUserAndFreePackage() {
        GoogleOidcIdentity identity = identity();
        when(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        )).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(identity.email())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        User result = service.resolve(GoogleAuthIntent.REGISTER, identity, CLIENT_INFO);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(userCaptor.getValue().getProviderSubject()).isEqualTo(identity.subject());
        assertThat(userCaptor.getValue().getPassword()).isNull();
        assertThat(result.getId()).isEqualTo(10L);
        verify(userPackageService).ensureFreePackage(10L);
    }

    @Test
    void resolve_whenLoginIntentMatchesGoogleUser_thenReturnExistingUser() {
        GoogleOidcIdentity identity = identity();
        User user = googleUser(identity);
        when(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        )).thenReturn(Optional.of(user));

        User result = service.resolve(GoogleAuthIntent.LOGIN, identity, CLIENT_INFO);

        assertThat(result).isSameAs(user);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolve_whenLoginIntentHasNoGoogleUser_thenReject() {
        GoogleOidcIdentity identity = identity();
        when(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(
                GoogleAuthIntent.LOGIN,
                identity,
                CLIENT_INFO
        )).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolve_whenRegisterIntentAndGoogleUserExists_thenReturnExistingUser() {
        GoogleOidcIdentity identity = identity();
        User user = googleUser(identity);
        when(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        )).thenReturn(Optional.of(user));

        User result = service.resolve(GoogleAuthIntent.REGISTER, identity, CLIENT_INFO);

        assertThat(result).isSameAs(user);
        verify(userRepository, never()).saveAndFlush(any());
        verify(userPackageService, never()).ensureFreePackage(any());
    }

    @Test
    void resolve_whenRegisterEmailBelongsToBasicUser_thenReject() {
        GoogleOidcIdentity identity = identity();
        when(userRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                identity.subject()
        )).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(identity.email())).thenReturn(true);

        assertThatThrownBy(() -> service.resolve(
                GoogleAuthIntent.REGISTER,
                identity,
                CLIENT_INFO
        )).isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolve_whenEmailIsNotVerified_thenReject() {
        GoogleOidcIdentity identity = new GoogleOidcIdentity(
                "google-subject",
                "user@example.com",
                "Tarik",
                "Test",
                false
        );

        assertThatThrownBy(() -> service.resolve(
                GoogleAuthIntent.LOGIN,
                identity,
                CLIENT_INFO
        )).isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).findByProviderAndProviderSubject(any(), any());
    }

    private GoogleOidcIdentity identity() {
        return new GoogleOidcIdentity(
                "google-subject",
                "user@example.com",
                "Tarik",
                "Test",
                true
        );
    }

    private User googleUser(GoogleOidcIdentity identity) {
        return User.builder()
                .id(10L)
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .email(identity.email())
                .provider(AuthProvider.GOOGLE)
                .providerSubject(identity.subject())
                .build();
    }
}
