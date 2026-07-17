package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.security.GoogleOAuthPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class GoogleOAuthStartupValidator implements ApplicationRunner {

    private final GoogleOAuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String frontendCallbackUrl = properties.frontendCallbackUrl();
        if (frontendCallbackUrl == null || frontendCallbackUrl.isBlank()) {
            throw new IllegalStateException("google.oauth.frontend-callback-url boş olamaz");
        }

        URI uri = URI.create(frontendCallbackUrl);
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "google.oauth.frontend-callback-url geçersiz: " + frontendCallbackUrl
            );
        }

        String normalized = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;

        if (GoogleOAuthPaths.CALLBACK.equals(normalized)
                || GoogleOAuthPaths.LEGACY_CALLBACK.equals(normalized)
                || normalized.contains("/login/oauth2/code")) {
            throw new IllegalStateException(
                    "GOOGLE_FRONTEND_CALLBACK_URL API OAuth callback yolunu göstermemelidir. "
                            + "Beklenen örnek: http://localhost:3000/api/auth/google/callback. "
                            + "Mevcut: " + frontendCallbackUrl
            );
        }

        if (!normalized.endsWith("/api/auth/google/callback")) {
            throw new IllegalStateException(
                    "GOOGLE_FRONTEND_CALLBACK_URL Next.js callback yolunu göstermelidir. "
                            + "Beklenen örnek: http://localhost:3000/api/auth/google/callback. "
                            + "Mevcut: " + frontendCallbackUrl
            );
        }
    }
}
