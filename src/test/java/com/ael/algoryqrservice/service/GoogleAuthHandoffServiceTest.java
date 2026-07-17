package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.GoogleAuthHandoffTicket;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.repository.GoogleAuthHandoffTicketRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthHandoffServiceTest {

    private static final ClientInfo CLIENT_INFO = new ClientInfo(
            "127.0.0.1",
            "test-agent",
            "test-device",
            "browser"
    );

    @Mock
    private GoogleAuthHandoffTicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private SecureRandom secureRandom;

    private GoogleAuthHandoffService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthHandoffService(
                ticketRepository,
                userRepository,
                sessionService,
                new GoogleOAuthProperties(
                        "http://localhost:3000/api/auth/google/callback",
                        Duration.ofMinutes(2)
                ),
                secureRandom
        );
    }

    @Test
    void issue_whenUserIsResolved_thenStoreOnlyTicketHash() {
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) index;
            }
            return null;
        }).when(secureRandom).nextBytes(any());

        String rawTicket = service.issue(10L, GoogleAuthIntent.LOGIN);

        ArgumentCaptor<GoogleAuthHandoffTicket> ticketCaptor =
                ArgumentCaptor.forClass(GoogleAuthHandoffTicket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        GoogleAuthHandoffTicket stored = ticketCaptor.getValue();
        assertThat(rawTicket).isNotBlank();
        assertThat(stored.getTicketHash()).hasSize(64).isNotEqualTo(rawTicket);
        assertThat(stored.getUserId()).isEqualTo(10L);
        assertThat(stored.getIntent()).isEqualTo(GoogleAuthIntent.LOGIN);
        assertThat(stored.isConsumed()).isFalse();
    }

    @Test
    void redeem_whenTicketIsActive_thenConsumeAndCreateSession() {
        String rawTicket = "opaque-ticket";
        GoogleAuthHandoffTicket storedTicket = GoogleAuthHandoffTicket.builder()
                .userId(10L)
                .intent(GoogleAuthIntent.REGISTER)
                .build();
        User user = User.builder().id(10L).email("user@example.com").build();
        SessionService.SessionTokens tokens =
                new SessionService.SessionTokens(null, "access", "refresh", user);
        AuthResponse response = AuthResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .build();
        when(ticketRepository.consume(any(), any())).thenReturn(1);
        when(ticketRepository.findByTicketHash(any())).thenReturn(Optional.of(storedTicket));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(sessionService.createSession(user, CLIENT_INFO)).thenReturn(tokens);
        when(sessionService.buildAuthResponse("access", "refresh")).thenReturn(response);

        AuthResponse result = service.redeem(rawTicket, CLIENT_INFO);

        assertThat(result).isSameAs(response);
        verify(ticketRepository).consume(any(), any());
        verify(sessionService).createSession(user, CLIENT_INFO);
    }

    @Test
    void redeem_whenTicketWasAlreadyConsumed_thenRejectWithoutSession() {
        when(ticketRepository.consume(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.redeem("opaque-ticket", CLIENT_INFO))
                .isInstanceOf(UnauthorizedException.class);

        verify(ticketRepository, never()).findByTicketHash(any());
        verify(sessionService, never()).createSession(any(), eq(CLIENT_INFO));
    }
}
