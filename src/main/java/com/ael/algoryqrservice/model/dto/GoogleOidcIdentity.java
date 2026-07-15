package com.ael.algoryqrservice.model.dto;

public record GoogleOidcIdentity(
        String subject,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified
) {
}
