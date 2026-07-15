package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRedeemRequest(
        @NotBlank(message = "Handoff ticket zorunludur")
        String ticket
) {
}
