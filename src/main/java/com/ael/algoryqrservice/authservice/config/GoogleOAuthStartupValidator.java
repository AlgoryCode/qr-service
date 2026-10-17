package com.ael.algoryqrservice.authservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthStartupValidator.class);

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthStartupValidator(
            ClientRegistrationRepository clientRegistrationRepository,
            GoogleOAuthProperties properties
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        if (google == null) {
            log.error("Google OAuth client registration 'google' is missing");
            return;
        }

        String redirectUri = google.getRedirectUri();
        log.info("Google OAuth redirect URI (register this exactly in Google Cloud Console): {}", redirectUri);
        log.info("Google OAuth frontend callback after login: {}", properties.frontendCallbackUrl());
        log.info(
                "Google OAuth handoff: browser lands on frontend callback with ?ticket=; "
                        + "monolith can redeem at GET /api/auth/google/callback on this service when "
                        + "GOOGLE_FRONTEND_CALLBACK_URL uses this host (not prod.auth.*)"
        );
        log.info(
                "If Google shows redirect_uri_mismatch, add the redirect URI above under "
                        + "APIs & Services > Credentials > OAuth 2.0 Client > Authorized redirect URIs"
        );
    }
}
