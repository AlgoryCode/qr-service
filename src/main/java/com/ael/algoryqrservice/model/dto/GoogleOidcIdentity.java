package com.ael.algoryqrservice.model.dto;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record GoogleOidcIdentity(
        String subject,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified
) {
    public static GoogleOidcIdentity from(OidcUser oidcUser) {
        Boolean verified = oidcUser.getEmailVerified();
        return new GoogleOidcIdentity(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                verified != null && verified
        );
    }
}
