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
import java.util.UUID;

@Service
public class TokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(TokenUseCase.class);

    private final SessionJpaRepository sessionRepository;
    private final SessionIssuer sessionIssuer;
    private final SubjectLoader subjectLoader;
    private final AuthResponseAssembler assembler;
    private final MerchantPackageGate merchantPackageGate;

    public TokenUseCase(
            SessionJpaRepository sessionRepository,
            SessionIssuer sessionIssuer,
            SubjectLoader subjectLoader,
            AuthResponseAssembler assembler,
            MerchantPackageGate merchantPackageGate
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionIssuer = sessionIssuer;
        this.subjectLoader = subjectLoader;
        this.assembler = assembler;
        this.merchantPackageGate = merchantPackageGate;
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

    @AuthTransactional
    public Map<String, Object> refresh(String refreshToken, ClientInfo clientInfo) {
        RefreshMatch match = openSessionForRefresh(refreshToken);
        SessionEntity session = match.session();
        if (session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Refresh token expired");
        }
        AuthSubject subject = subjectLoader.load(session.getSubjectType(), session.getSubjectId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        merchantPackageGate.assertOpen(subject);
        return assembler.assemble(sessionIssuer.rotate(session, subject, clientInfo, match.retryOfPrevious()), subject);
    }

    private record RefreshMatch(SessionEntity session, boolean retryOfPrevious) {
    }

    @AuthTransactional
    public void expire(String refreshToken) {
        SessionEntity session = openSession(refreshToken);
        LocalDateTime now = LocalDateTime.now();
        session.setAccessExpiresAt(now);
        session.setRefreshExpiresAt(now);
        session.setLastActivityAt(now);
        sessionRepository.save(session);
        log.info("Auth session expired sessionId={} subjectType={}", session.getId(), session.getSubjectType());
    }

    @AuthTransactional
    public void revoke(String refreshToken) {
        SessionEntity session = openSession(refreshToken);
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
            // Own transaction: the revoke must outlive this request's rollback on the 401 below.
            sessionIssuer.revokeNow(session.getId());
            log.warn("Rotated refresh token reused, session revoked sessionId={} subjectType={}", session.getId(), session.getSubjectType());
            throw new UnauthorizedException(UnauthorizedException.SESSION_REUSED, "Refresh token reused");
        }
        throw new UnauthorizedException(UnauthorizedException.SESSION_EXPIRED, "Invalid refresh token");
    }

    /** For sign-out: the current token, or the replaced one while a client may still hold it. */
    private SessionEntity openSession(String refreshToken) {
        SessionIssuer.RefreshTokenParts parts = sessionIssuer.parseRefreshToken(refreshToken);
        SessionEntity session = sessionRepository.findByIdAndRevokedFalse(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        boolean current = sessionIssuer.matchesRefresh(session, parts.rawToken());
        if (!current && !(inGrace(session) && sessionIssuer.matchesPreviousRefresh(session, parts.rawToken()))) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        return session;
    }

    private static boolean inGrace(SessionEntity session) {
        LocalDateTime validUntil = session.getPreviousRefreshValidUntil();
        return validUntil != null && validUntil.isAfter(LocalDateTime.now());
    }
}
