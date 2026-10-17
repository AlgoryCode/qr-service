package com.ael.algoryqrservice.authservice.service;

import com.ael.algoryqrservice.authservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.GoogleAuthHandoffEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.GoogleAuthHandoffRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;
import com.ael.algoryqrservice.authservice.application.auth.MerchantPackageGate;
import com.ael.algoryqrservice.authservice.application.auth.SubjectLoader;
import com.ael.algoryqrservice.authservice.application.auth.TokenUseCase;

@Service
@RequiredArgsConstructor
public class GoogleAuthHandoffService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthHandoffService.class);

    private static final int TICKET_BYTE_LENGTH = 32;

    private final GoogleAuthHandoffRepository ticketRepository;
    private final SubjectLoader subjectLoader;
    private final TokenUseCase tokenUseCase;
    private final MerchantPackageGate merchantPackageGate;
    private final GoogleOAuthProperties properties;
    private final SecureRandom secureRandom;

    @AuthTransactional
    public String issue(AuthSubject subject, GoogleAuthIntent intent, String audience) {
        byte[] ticketBytes = new byte[TICKET_BYTE_LENGTH];
        secureRandom.nextBytes(ticketBytes);
        String rawTicket = Base64.getUrlEncoder().withoutPadding().encodeToString(ticketBytes);
        GoogleAuthHandoffEntity ticket = new GoogleAuthHandoffEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setTicketHash(hash(rawTicket));
        ticket.setSubjectType(subject.subjectType());
        ticket.setSubjectId(subject.subjectId());
        ticket.setAudience(audience);
        ticket.setIntent(intent.name());
        ticket.setExpiresAt(LocalDateTime.now().plus(properties.handoffTicketTtl()));
        ticket.setConsumed(false);
        ticketRepository.save(ticket);
        return rawTicket;
    }

    @AuthTransactional
    public Map<String, Object> redeem(String rawTicket, ClientInfo clientInfo) {
        String ticketHash = hashRequired(rawTicket);
        LocalDateTime now = LocalDateTime.now();
        GoogleAuthHandoffEntity ticket = ticketRepository.findByTicketHash(ticketHash)
                .filter(found -> !found.isConsumed())
                .filter(found -> found.getExpiresAt().isAfter(now))
                .orElseThrow(() -> {
                    log.warn(
                            "Google OAuth handoff redeem failed: invalid or expired ticket clientIp={}",
                            clientInfo.ipAddress()
                    );
                    return new UnauthorizedException("Handoff ticket geçersiz, kullanılmış veya süresi dolmuş");
                });
        AuthSubject subject = subjectLoader.load(ticket.getSubjectType(), ticket.getSubjectId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        merchantPackageGate.assertOpen(subject);
        if (ticketRepository.consume(ticketHash, now) != 1) {
            log.warn("Google OAuth handoff redeem failed: ticket race clientIp={}", clientInfo.ipAddress());
            throw new UnauthorizedException("Handoff ticket geçersiz, kullanılmış veya süresi dolmuş");
        }
        log.info(
                "Google OAuth handoff redeemed subjectType={} subjectId={} intent={} clientIp={}",
                subject.subjectType(),
                subject.subjectId(),
                ticket.getIntent(),
                clientInfo.ipAddress()
        );
        return tokenUseCase.create(subject, clientInfo);
    }

    private String hashRequired(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw new UnauthorizedException("Handoff ticket zorunludur");
        }
        return hash(rawTicket);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Handoff ticket hash algoritması kullanılamıyor", exception);
        }
    }
}
