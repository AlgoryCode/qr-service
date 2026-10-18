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
        SessionEntity session = openSession(refreshToken);
        if (session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }
        AuthSubject subject = subjectLoader.load(session.getSubjectType(), session.getSubjectId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        merchantPackageGate.assertOpen(subject);
        return assembler.assemble(sessionIssuer.rotate(session, subject, clientInfo), subject);
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

    private SessionEntity openSession(String refreshToken) {
        SessionIssuer.RefreshTokenParts parts = sessionIssuer.parseRefreshToken(refreshToken);
        SessionEntity session = sessionRepository.findByIdAndRevokedFalse(parts.sessionId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!sessionIssuer.matchesRefresh(session, parts.rawToken())) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        return session;
    }
}
