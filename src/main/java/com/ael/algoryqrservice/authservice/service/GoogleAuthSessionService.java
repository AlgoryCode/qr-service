package com.ael.algoryqrservice.authservice.service;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;
import com.ael.algoryqrservice.authservice.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthSessionService.class);

    private static final String INTENT = "googleAuthIntent";
    private static final String AUDIENCE = "googleAuthAudience";
    private static final String PENDING_COOKIE = "google-oauth-pending";
    private static final int PENDING_MAX_AGE_SECONDS = 600;

    private final JwtProperties jwtProperties;

    public void store(
            HttpServletRequest request,
            HttpServletResponse response,
            GoogleAuthIntent intent,
            String audience
    ) {
        HttpSession session = request.getSession(true);
        session.setAttribute(INTENT, intent);
        session.setAttribute(AUDIENCE, audience);
        attachPendingCookie(response, intent, audience);
        log.info(
                "Google OAuth flow started intent={} audience={} sessionId={}",
                intent.value(),
                audience,
                session.getId()
        );
    }

    public Optional<PendingGoogleAuth> find(HttpServletRequest request) {
        Optional<PendingGoogleAuth> fromSession = findInSession(request);
        if (fromSession.isPresent()) {
            return fromSession;
        }
        Optional<PendingGoogleAuth> fromCookie = findInCookie(request);
        if (fromCookie.isPresent()) {
            log.warn("Google OAuth pending context recovered from cookie (HTTP session was missing)");
        }
        return fromCookie;
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        clearPendingCookie(response);
    }

    private Optional<PendingGoogleAuth> findInSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object intent = session.getAttribute(INTENT);
        Object audience = session.getAttribute(AUDIENCE);
        if (!(intent instanceof GoogleAuthIntent googleAuthIntent)) {
            return Optional.empty();
        }
        return Optional.of(new PendingGoogleAuth(googleAuthIntent, resolveAudience(audience)));
    }

    private Optional<PendingGoogleAuth> findInCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (!PENDING_COOKIE.equals(cookie.getName())) {
                continue;
            }
            return parsePendingCookie(cookie.getValue());
        }
        return Optional.empty();
    }

    private void attachPendingCookie(HttpServletResponse response, GoogleAuthIntent intent, String audience) {
        long expiresAt = Instant.now().getEpochSecond() + PENDING_MAX_AGE_SECONDS;
        String payload = intent.value() + "|" + resolveAudience(audience) + "|" + expiresAt;
        String signed = sign(payload);
        Cookie cookie = new Cookie(PENDING_COOKIE, signed);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(PENDING_MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    private void clearPendingCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(PENDING_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Optional<PendingGoogleAuth> parsePendingCookie(String value) {
        if (value == null || value.isBlank() || !value.contains(".")) {
            return Optional.empty();
        }
        int separator = value.lastIndexOf('.');
        if (separator <= 0) {
            return Optional.empty();
        }
        String encodedPayload = value.substring(0, separator);
        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        if (!sign(payload).equals(value)) {
            log.warn("Google OAuth pending cookie signature mismatch");
            return Optional.empty();
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            long expiresAt = Long.parseLong(parts[2]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                log.warn("Google OAuth pending cookie expired");
                return Optional.empty();
            }
            GoogleAuthIntent intent = GoogleAuthIntent.from(parts[0]);
            return Optional.of(new PendingGoogleAuth(intent, resolveAudience(parts[1])));
        } catch (RuntimeException exception) {
            log.warn("Google OAuth pending cookie could not be parsed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return encodedPayload + "." + encodedSignature;
        } catch (Exception exception) {
            throw new IllegalStateException("Google OAuth pending cookie imzalanamadı", exception);
        }
    }

    private static String resolveAudience(Object audience) {
        if (audience instanceof String value && !value.isBlank()) {
            return value.trim().toUpperCase(Locale.ROOT);
        }
        return AuthSubject.TYPE_MERCHANT;
    }

    public record PendingGoogleAuth(GoogleAuthIntent intent, String audience) {
    }
}
