package com.ael.algoryqrservice.qrservice.model.dto;

import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.qrservice.model.enums.UserRole;

public record SessionUserResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        AuthProvider provider,
        UserRole role
) {
}
