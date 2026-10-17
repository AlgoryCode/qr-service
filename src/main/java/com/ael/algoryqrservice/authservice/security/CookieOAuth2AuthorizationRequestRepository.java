package com.ael.algoryqrservice.authservice.security;

import com.ael.algoryqrservice.authservice.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Stores the OAuth2 authorization request in a signed browser cookie so Google callbacks work
 * without HTTP session affinity (and survive redirects before {@code JSESSIONID} is established).
 */
@Component
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2-auth-req";
    private static final int MAX_AGE_SECONDS = 600;

    private final JwtProperties jwtProperties;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            clearCookie(response, request.isSecure());
            return;
        }
        writeCookie(response, authorizationRequest, request.isSecure());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        clearCookie(response, request.isSecure());
        return authorizationRequest;
    }

    private void writeCookie(HttpServletResponse response, OAuth2AuthorizationRequest request, boolean secure) {
        byte[] serialized = serialize(request);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(serialized);
        String signed = sign(encodedPayload);
        Cookie cookie = new Cookie(COOKIE_NAME, signed);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, boolean secure) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Optional<OAuth2AuthorizationRequest> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            return parseSignedValue(cookie.getValue());
        }
        return Optional.empty();
    }

    private Optional<OAuth2AuthorizationRequest> parseSignedValue(String value) {
        if (value == null || value.isBlank() || !value.contains(".")) {
            return Optional.empty();
        }
        int separator = value.lastIndexOf('.');
        if (separator <= 0) {
            return Optional.empty();
        }
        String encodedPayload = value.substring(0, separator);
        if (!sign(encodedPayload).equals(value)) {
            return Optional.empty();
        }
        try {
            byte[] serialized = Base64.getUrlDecoder().decode(encodedPayload);
            return Optional.of(deserialize(serialized));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return encodedPayload + "." + encodedSignature;
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth2 authorization cookie imzalanamadi", exception);
        }
    }

    private static byte[] serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(request);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("OAuth2 authorization request serilestirilemedi", exception);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(byte[] serialized) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = in.readObject();
            if (!(value instanceof OAuth2AuthorizationRequest authorizationRequest)) {
                throw new IllegalStateException("Unexpected OAuth2 authorization cookie payload");
            }
            return authorizationRequest;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("OAuth2 authorization request okunamadi", exception);
        }
    }
}
