package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUseCaseTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String TOKEN = "refresh-jwt";
    private static final String RAW = "raw";

    @Mock
    private SessionJpaRepository sessionRepository;
    @Mock
    private SessionIssuer sessionIssuer;
    @Mock
    private SubjectLoader subjectLoader;
    @Mock
    private AuthResponseAssembler assembler;
    @Mock
    private ActiveSessionRegistry activeSessions;

    @InjectMocks
    private TokenUseCase tokenUseCase;

    @Test
    void refresh_withCurrentToken_thenRotates() {
        SessionEntity session = openSession();
        stubLookup(session);
        when(sessionIssuer.matchesRefresh(session, RAW)).thenReturn(true);
        stubRotation(session, false);

        assertThat(tokenUseCase.refresh(TOKEN, null)).containsKey("accessToken");
    }

    @Test
    void refresh_withReplacedTokenInsideGrace_thenRotatesInsteadOfSigningOut() {
        SessionEntity session = openSession();
        session.setPreviousRefreshValidUntil(LocalDateTime.now().plusSeconds(30));
        stubLookup(session);
        when(sessionIssuer.matchesRefresh(session, RAW)).thenReturn(false);
        when(sessionIssuer.matchesPreviousRefresh(session, RAW)).thenReturn(true);
        stubRotation(session, true);

        assertThat(tokenUseCase.refresh(TOKEN, null)).containsKey("accessToken");
        verify(sessionIssuer, never()).revoke(any());
    }

    @Test
    void refresh_withReplacedTokenAfterGrace_thenRevokesSessionAsReused() {
        SessionEntity session = openSession();
        session.setPreviousRefreshValidUntil(LocalDateTime.now().minusSeconds(1));
        stubLookup(session);
        when(sessionIssuer.matchesRefresh(session, RAW)).thenReturn(false);
        when(sessionIssuer.matchesPreviousRefresh(session, RAW)).thenReturn(true);

        assertThatThrownBy(() -> tokenUseCase.refresh(TOKEN, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(UnauthorizedException.SESSION_REUSED));
        verify(sessionIssuer).revoke(session);
    }

    @Test
    void refresh_onRevokedSession_thenSaysSignedOut() {
        SessionEntity session = openSession();
        session.setRevoked(true);
        stubLookup(session);

        assertThatThrownBy(() -> tokenUseCase.refresh(TOKEN, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(UnauthorizedException.SESSION_REVOKED));
    }

    @Test
    void refresh_withUnknownToken_thenSaysExpired() {
        SessionEntity session = openSession();
        stubLookup(session);
        when(sessionIssuer.matchesRefresh(session, RAW)).thenReturn(false);
        when(sessionIssuer.matchesPreviousRefresh(session, RAW)).thenReturn(false);

        assertThatThrownBy(() -> tokenUseCase.refresh(TOKEN, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(UnauthorizedException.SESSION_EXPIRED));
    }

    @Test
    void revokeOthers_keepsCurrentSessionAndClosesTheRest() {
        SessionEntity current = sessionWithId(SESSION_ID);
        SessionEntity phone = sessionWithId(UUID.randomUUID());
        SessionEntity tablet = sessionWithId(UUID.randomUUID());
        when(sessionRepository.findBySubjectTypeAndSubjectIdAndRevokedFalse(AuthSubject.TYPE_MERCHANT, 86L))
                .thenReturn(List.of(current, phone, tablet));

        int revoked = tokenUseCase.revokeOthers(AuthSubject.TYPE_MERCHANT, 86L, SESSION_ID);

        assertThat(revoked).isEqualTo(2);
        verify(sessionIssuer).revoke(phone);
        verify(sessionIssuer).revoke(tablet);
        verify(sessionIssuer, never()).revoke(current);
    }

    @Test
    void revoke_withUnknownSession_isNoOp() {
        when(sessionIssuer.parseRefreshToken(TOKEN)).thenReturn(new SessionIssuer.RefreshTokenParts(SESSION_ID, RAW));
        when(sessionRepository.findByIdAndRevokedFalse(SESSION_ID)).thenReturn(Optional.empty());

        tokenUseCase.revoke(TOKEN);

        verify(sessionIssuer, never()).revoke(any());
    }

    @Test
    void revoke_withMalformedToken_isNoOp() {
        when(sessionIssuer.parseRefreshToken("garbage")).thenThrow(new UnauthorizedException("Invalid refresh token format"));

        tokenUseCase.revoke("garbage");

        verify(sessionIssuer, never()).revoke(any());
    }

    @Test
    void revoke_withCurrentToken_revokesSession() {
        SessionEntity session = openSession();
        when(sessionIssuer.parseRefreshToken(TOKEN)).thenReturn(new SessionIssuer.RefreshTokenParts(SESSION_ID, RAW));
        when(sessionRepository.findByIdAndRevokedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionIssuer.matchesRefresh(session, RAW)).thenReturn(true);

        tokenUseCase.revoke(TOKEN);

        verify(sessionIssuer).revoke(session);
    }

    @Test
    void expire_withAlreadyEndedSession_isNoOp() {
        when(sessionIssuer.parseRefreshToken(TOKEN)).thenReturn(new SessionIssuer.RefreshTokenParts(SESSION_ID, RAW));
        when(sessionRepository.findByIdAndRevokedFalse(SESSION_ID)).thenReturn(Optional.empty());

        tokenUseCase.expire(TOKEN);

        verify(sessionRepository, never()).save(any());
    }

    private void stubLookup(SessionEntity session) {
        when(sessionIssuer.parseRefreshToken(TOKEN)).thenReturn(new SessionIssuer.RefreshTokenParts(SESSION_ID, RAW));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void stubRotation(SessionEntity session, boolean retryOfPrevious) {
        AuthSubject subject = new AuthSubject(AuthSubject.TYPE_MERCHANT, 86L, null, "MERCHANT", null, null, "m", null, 86L, null, List.of());
        when(subjectLoader.load(AuthSubject.TYPE_MERCHANT, 86L)).thenReturn(Optional.of(subject));
        SessionIssuer.IssuedTokens tokens = new SessionIssuer.IssuedTokens("a", "r", SESSION_ID, List.of(), null, null);
        when(sessionIssuer.rotate(eq(session), eq(subject), any(), eq(retryOfPrevious))).thenReturn(tokens);
        when(assembler.assemble(tokens, subject)).thenReturn(Map.of("accessToken", "a"));
    }

    private static SessionEntity openSession() {
        return sessionWithId(SESSION_ID);
    }

    private static SessionEntity sessionWithId(UUID id) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSubjectType(AuthSubject.TYPE_MERCHANT);
        session.setSubjectId(86L);
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        return session;
    }
}
