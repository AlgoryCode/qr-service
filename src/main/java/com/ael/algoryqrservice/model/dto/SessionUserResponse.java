package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;

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
