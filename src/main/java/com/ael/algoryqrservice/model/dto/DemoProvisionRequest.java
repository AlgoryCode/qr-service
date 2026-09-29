package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DemoProvisionRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName
) {
}
