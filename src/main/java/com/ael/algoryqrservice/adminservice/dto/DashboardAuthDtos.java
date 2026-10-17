package com.ael.algoryqrservice.adminservice.dto;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;

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
