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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GoogleAuthHandoffService {

    private static final int TICKET_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final GoogleAuthHandoffTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final GoogleOAuthProperties properties;
    private final SecureRandom secureRandom;

    @Transactional
    public String issue(Long userId, GoogleAuthIntent intent) {
        byte[] ticketBytes = new byte[TICKET_BYTE_LENGTH];
        secureRandom.nextBytes(ticketBytes);
        String rawTicket = Base64.getUrlEncoder().withoutPadding().encodeToString(ticketBytes);
        GoogleAuthHandoffTicket ticket = GoogleAuthHandoffTicket.builder()
                .ticketHash(hash(rawTicket))
                .userId(userId)
                .intent(intent)
                .expiresAt(LocalDateTime.now().plus(properties.handoffTicketTtl()))
                .build();
        ticketRepository.save(ticket);
        return rawTicket;
    }

    @Transactional
    public AuthResponse redeem(String rawTicket, ClientInfo clientInfo) {
        String ticketHash = hashRequired(rawTicket);
        LocalDateTime consumedAt = LocalDateTime.now();
        if (ticketRepository.consume(ticketHash, consumedAt) != 1) {
            throw new UnauthorizedException("Handoff ticket geçersiz, kullanılmış veya süresi dolmuş");
        }
        GoogleAuthHandoffTicket ticket = ticketRepository.findByTicketHash(ticketHash)
                .orElseThrow(() -> new UnauthorizedException("Handoff ticket geçersiz"));
        User user = userRepository.findById(ticket.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı"));
        SessionService.SessionTokens tokens = sessionService.createSession(user, clientInfo);
        return sessionService.buildAuthResponse(tokens.accessToken(), tokens.refreshToken());
    }

    private String hashRequired(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw new UnauthorizedException("Handoff ticket zorunludur");
        }
        return hash(rawTicket);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Handoff ticket hash algoritması kullanılamıyor", exception);
        }
    }
}
