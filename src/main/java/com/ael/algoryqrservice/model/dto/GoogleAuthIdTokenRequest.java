package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthIdTokenRequest(
        @NotBlank(message = "Google kimlik jetonu zorunludur")
        String idToken,
        @NotBlank(message = "Google kimlik doğrulama amacı zorunludur")
        String intent
) {
}
