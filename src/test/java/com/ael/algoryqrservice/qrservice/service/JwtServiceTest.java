package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.account.service.JwtService;

import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.config.JwtProperties;
import com.ael.algoryqrservice.qrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import com.ael.algoryqrservice.qrservice.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generateDashboardAccessToken_whenAdmin_thenIncludeAdminRoleAndDashboardPrincipal() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789012345678901234567890123456789012345678901234567890123");
        JwtService jwtService = new JwtService(properties);

        String token = jwtService.generateDashboardAccessToken(
                "admin@example.com",
                UUID.randomUUID(),
                7L,
                DashboardRole.ADMIN
        );

        Claims claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertThat(claims.get(JwtService.PRINCIPAL_TYPE_CLAIM)).isEqualTo(JwtService.PRINCIPAL_DASHBOARD);
        assertThat(jwtService.isDashboardPrincipal(claims)).isTrue();
        assertThat(jwtService.extractRoles(claims)).containsExactly("ROLE_ADMIN");
        assertThat(jwtService.extractDashboardUserId(claims)).isEqualTo(7L);
    }

}
