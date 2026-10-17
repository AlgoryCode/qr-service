package com.ael.algoryqrservice.qrservice.model.dto;

import com.ael.algoryqrservice.qrservice.model.enums.DashboardRole;

public final class DashboardAuthDtos {

    private DashboardAuthDtos() {
    }

    public record MeResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            DashboardRole role,
            boolean active
    ) {
    }
}
