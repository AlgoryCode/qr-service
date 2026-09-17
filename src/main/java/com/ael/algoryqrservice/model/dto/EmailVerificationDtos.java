package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class EmailVerificationDtos {
    private EmailVerificationDtos() {
    }

    public record Status(boolean verified, String email, LocalDateTime expiresAt) {
    }

    public record VerifyRequest(@NotBlank String code) {
    }

    public record ResendByEmailRequest(
            @NotBlank(message = "E-posta zorunludur")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            String email
    ) {
    }

    public record PublicVerifyRequest(
            @NotBlank(message = "E-posta zorunludur")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            String email,
            @NotBlank(message = "Doğrulama kodu zorunludur")
            String code
    ) {
    }
}
