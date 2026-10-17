package com.ael.algoryqrservice.authservice.application.auth;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Locale;

public record GoogleOidcIdentity(
        String subject,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified
) {
    public static GoogleOidcIdentity from(OidcUser oidcUser) {
        Boolean verified = oidcUser.getEmailVerified();
        String email = oidcUser.getEmail() == null ? "" : oidcUser.getEmail().trim().toLowerCase(Locale.ROOT);
        return new GoogleOidcIdentity(
                oidcUser.getSubject(),
                email,
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                verified != null && verified
        );
    }
}
