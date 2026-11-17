package com.ael.algoryqrservice.qrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Dashboard (admin) login: a username, or an e-mail address for accounts without one. */
@Data
public class LoginRequest {

    @Size(max = 255)
    private String username;

    @Size(max = 255)
    private String email;

    @NotBlank(message = "Şifre zorunludur")
    private String password;

    /** What the user typed to identify themselves, trimmed; empty when neither field was sent. */
    public String identifier() {
        String value = username != null && !username.isBlank() ? username : email;
        return value == null ? "" : value.trim();
    }
}
