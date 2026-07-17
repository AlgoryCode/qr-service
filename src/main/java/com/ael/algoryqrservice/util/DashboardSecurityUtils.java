package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.repository.DashboardUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardSecurityUtils {

    private final DashboardUserRepository dashboardUserRepository;

    public DashboardUser getCurrentDashboardUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return dashboardUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Dashboard kullanicisi bulunamadi"));
    }
}
