package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.security.JwtTokenCodec;
import com.ael.algoryqrservice.authservice.config.JwtProperties;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionIssuerRotateTest {

    @Mock
    private SessionJpaRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginPackageClaims loginPackageClaims;

    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "test", "test", "TEST");

    private SessionIssuer issuer;
    private final AuthSubject merchant = new AuthSubject(
            AuthSubject.TYPE_MERCHANT, 86L, null, "MERCHANT", null, null, "m", null, 86L, null, List.of());

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("x".repeat(64));
        issuer = new SessionIssuer(sessionRepository, passwordEncoder, properties, loginPackageClaims,
                new JwtTokenCodec("test-only-jwt-secret-0123456789abcdef0123456789abcdef", "algoryqr", "algoryqr-api"),
                org.mockito.Mockito.mock(ActiveSessionRegistry.class));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        lenient().when(loginPackageClaims.resolve(any())).thenReturn(LoginPackageClaims.Snapshot.none());
    }

    @Test
    void rotate_keepsTheReplacedTokenUsableForTheGraceWindow() {
        SessionEntity session = session(LocalDateTime.now());

        issuer.rotate(session, merchant, CLIENT, false);

        assertThat(session.getPreviousRefreshTokenHash()).isEqualTo("hash:T0");
        assertThat(session.getPreviousRefreshValidUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void rotate_onAGraceRetry_keepsTheOriginalPreviousTokenAndWindow() {
        SessionEntity session = session(LocalDateTime.now());
        session.setPreviousRefreshTokenHash("hash:OLD");
        LocalDateTime window = LocalDateTime.now().plusSeconds(20);
        session.setPreviousRefreshValidUntil(window);

        issuer.rotate(session, merchant, CLIENT, true);

        assertThat(session.getPreviousRefreshTokenHash()).isEqualTo("hash:OLD");
        assertThat(session.getPreviousRefreshValidUntil()).isEqualTo(window);
        assertThat(session.getRefreshTokenHash()).isNotEqualTo("hash:T0");
    }

    @Test
    void rotate_neverExtendsPastTheAbsoluteLifetime() {
        LocalDateTime signedIn = LocalDateTime.now().minusDays(28);
        SessionEntity session = session(signedIn);

        issuer.rotate(session, merchant, CLIENT, false);

        assertThat(session.getRefreshExpiresAt()).isBeforeOrEqualTo(signedIn.plusDays(30));
    }

    private static SessionEntity session(LocalDateTime loggedInAt) {
        SessionEntity session = new SessionEntity();
        session.setId(UUID.randomUUID());
        session.setSubjectType(AuthSubject.TYPE_MERCHANT);
        session.setSubjectId(86L);
        session.setRefreshTokenHash("hash:T0");
        session.setLoggedInAt(loggedInAt);
        return session;
    }
}
