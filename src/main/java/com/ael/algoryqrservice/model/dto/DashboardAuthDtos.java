package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.DashboardRole;

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
