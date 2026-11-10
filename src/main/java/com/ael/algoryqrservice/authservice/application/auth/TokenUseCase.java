package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.SessionJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(TokenUseCase.class);

    private final SessionJpaRepository sessionRepository;
    private final SessionIssuer sessionIssuer;
    private final SubjectLoader subjectLoader;
    private final AuthResponseAssembler assembler;
    private final ActiveSessionRegistry activeSessions;

    public TokenUseCase(
            SessionJpaRepository sessionRepository,
            SessionIssuer sessionIssuer,
            SubjectLoader subjectLoader,
            AuthResponseAssembler assembler,
            ActiveSessionRegistry activeSessions
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionIssuer = sessionIssuer;
        this.subjectLoader = subjectLoader;
        this.assembler = assembler;
        this.activeSessions = activeSessions;
    }

    @AuthTransactional
    public Map<String, Object> create(AuthSubject subject, ClientInfo clientInfo) {
        log.info(
                "Auth session created subjectType={} subjectId={} clientIp={}",
                subject.subjectType(),
                subject.subjectId(),
                clientInfo.ipAddress()
        );
        return assembler.assemble(sessionIssuer.issue(subject, clientInfo), subject);
    }

    /**
     * Refusals (401) keep what this transaction wrote: a reused refresh token revokes the session and must stay
     * revoked even though the request itself fails. Nothing else is written before a refusal.
     */
    @AuthTransactional(noRollbackFor = UnauthorizedException.class)
    public Map<String, Object> refresh(String refreshToken, ClientInfo clientInfo) {
        RefreshMatch match = openSessionForRefresh(refreshToken);
        SessionEntity session = match.session();
        if (session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Refresh token expired");
        }
        AuthSubject subject = subjectLoader.load(session.getSubjectType(), session.getSubjectId())
                .orElseThrow(() -> new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Subject not found"));
        return assembler.assemble(sessionIssuer.rotate(session, subject, clientInfo, match.retryOfPrevious()), subject);
    }

    private record RefreshMatch(SessionEntity session, boolean retryOfPrevious) {
    }

    /** Sign-out is idempotent: an unknown, already ended or malformed token is a no-op. */
    @AuthTransactional
    public void expire(String refreshToken) {
        Optional<SessionEntity> found = findSessionForSignOut(refreshToken);
        if (found.isEmpty()) {
            log.info("Logout ignored: session already ended or token unknown");
            return;
        }
        SessionEntity session = found.get();
        LocalDateTime now = LocalDateTime.now();
        session.setAccessExpiresAt(now);
        session.setRefreshExpiresAt(now);
        session.setLastActivityAt(now);
        sessionRepository.save(session);
        activeSessions.markEnded(session.getId());
        log.info("Auth session expired sessionId={} subjectType={}", session.getId(), session.getSubjectType());
    }

    /** Sign-out is idempotent: an unknown, already revoked or malformed token is a no-op. */
    @AuthTransactional
    public void revoke(String refreshToken) {
        Optional<SessionEntity> found = findSessionForSignOut(refreshToken);
        if (found.isEmpty()) {
            log.info("Logout ignored: session already ended or token unknown");
            return;
        }
        SessionEntity session = found.get();
        sessionIssuer.revoke(session);
        log.info("Auth session revoked sessionId={} subjectType={}", session.getId(), session.getSubjectType());
    }

    /**
     * Like {@link #openSession} but also takes the token the last rotation replaced while its grace window is open.
     * Outside the window that token was copied from somewhere: the session is revoked so neither copy works.
     */
    /**
     * Signs the account out everywhere but here: every other open session of the same account is revoked, so its
     * next refresh is refused with {@link UnauthorizedException#SESSION_REVOKED}. Returns how many were closed.
     */
    @AuthTransactional
    public int revokeOthers(String subjectType, Long subjectId, UUID currentSessionId) {
        if (subjectType == null || subjectId == null) {
            throw new UnauthorizedException("Oturum bulunamadı");
        }
        int revoked = 0;
        for (SessionEntity session : sessionRepository.findBySubjectTypeAndSubjectIdAndRevokedFalse(subjectType, subjectId)) {
            if (session.getId().equals(currentSessionId)) {
                continue;
            }
            sessionIssuer.revoke(session);
            revoked++;
        }
        log.info("Other sessions revoked subjectType={} subjectId={} count={}", subjectType, subjectId, revoked);
        return revoked;
    }

    /**
     * Ends every session of an account after its password changed (or it was deactivated), except [keepSessionId]
     * when the user changed it themselves from this device. Their next refresh is refused as signed out.
     */
    @AuthTransactional
    public int revokeAllExcept(String subjectType, Long subjectId, UUID keepSessionId) {
        return revokeOthers(subjectType, subjectId, keepSessionId);
    }

    private RefreshMatch openSessionForRefresh(String refreshToken) {
        SessionIssuer.RefreshTokenParts parts = sessionIssuer.parseRefreshToken(refreshToken);
        SessionEntity session = sessionRepository.findByIdForUpdate(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Invalid refresh token"));
        if (session.isRevoked()) {
            throw new UnauthorizedException(UnauthorizedException.SESSION_REVOKED, "Session signed out");
        }
        if (sessionIssuer.matchesRefresh(session, parts.rawToken())) {
            return new RefreshMatch(session, false);
        }
        if (sessionIssuer.matchesPreviousRefresh(session, parts.rawToken())) {
            if (inGrace(session)) {
                return new RefreshMatch(session, true);
            }
            // Same transaction and connection that already holds this row's FOR UPDATE lock. Revoking through a
            // separate transaction here deadlocked: the second connection waited on our own lock forever, every
            // other refresh of the session queued behind it, and the pool ran dry (prod, 10-11 Nov).
            sessionIssuer.revoke(session);
            log.warn("Rotated refresh token reused, session revoked sessionId={} subjectType={}", session.getId(), session.getSubjectType());
            throw new UnauthorizedException(UnauthorizedException.SESSION_REUSED, "Refresh token reused");
        }
        throw new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Invalid refresh token");
    }

    /**
     * For sign-out: the open session the current token belongs to, or the replaced one while a client may still
     * hold it. Anything else (malformed, unknown, revoked, mismatched) means there is nothing left to sign out.
     */
    private Optional<SessionEntity> findSessionForSignOut(String refreshToken) {
        SessionIssuer.RefreshTokenParts parts;
        try {
            parts = sessionIssuer.parseRefreshToken(refreshToken);
        } catch (UnauthorizedException malformed) {
            return Optional.empty();
        }
        return sessionRepository.findByIdAndRevokedFalse(parts.sessionId())
                .filter(session -> sessionIssuer.matchesRefresh(session, parts.rawToken())
                        || (inGrace(session) && sessionIssuer.matchesPreviousRefresh(session, parts.rawToken())));
    }

    private static boolean inGrace(SessionEntity session) {
        LocalDateTime validUntil = session.getPreviousRefreshValidUntil();
        return validUntil != null && validUntil.isAfter(LocalDateTime.now());
    }
}
