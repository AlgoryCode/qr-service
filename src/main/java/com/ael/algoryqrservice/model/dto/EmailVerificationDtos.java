package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class EmailVerificationDtos {
    private EmailVerificationDtos() {
    }

    public record Status(boolean verified, String email, LocalDateTime expiresAt) {
    }

    public record VerifyRequest(@NotBlank String code) {
    }
}
