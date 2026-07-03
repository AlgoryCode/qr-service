package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token zorunludur")
    private String refreshToken;
}
