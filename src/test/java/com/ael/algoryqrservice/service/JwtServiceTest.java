package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.config.JwtProperties;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.DashboardRole;
import com.ael.algoryqrservice.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generateAccessToken_whenProfileProvided_thenIncludePackageProductAndScopeClaims() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789012345678901234567890123456789012345678901234567890123");
        JwtService jwtService = new JwtService(properties);
        UserAccessProfile profile = new UserAccessProfile(
                CatalogPackages.PRO_PACKAGE,
                List.of(CatalogProducts.QR_CREATE, CatalogProducts.SMART_ASSISTANT),
                List.of(CatalogScopes.QR_CREATE_OWNER, CatalogScopes.SMART_ASSISTANT_OWNER)
        );

        String token = jwtService.generateAccessToken(
                "user@example.com",
                UUID.randomUUID(),
                42L,
                UserRole.USER,
                AuthProvider.GOOGLE,
                profile
        );

        Claims claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertThat(claims.get("activePackage")).isEqualTo("PRO_PACKAGE");
        assertThat(jwtService.extractProducts(claims)).containsExactly("QR_CREATE", "SMART_ASSISTANT");
        assertThat(jwtService.extractScopes(claims)).containsExactly("QR_CREATE_OWNER", "SMART_ASSISTANT_OWNER");
        assertThat(claims.get("provider")).isEqualTo("GOOGLE");
        assertThat(claims.get(JwtService.PRINCIPAL_TYPE_CLAIM)).isEqualTo(JwtService.PRINCIPAL_APP);
        assertThat(jwtService.isDashboardPrincipal(claims)).isFalse();
    }

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
        assertThat(claims.get("userId")).isEqualTo(7);
    }

    @Test
    void generateCustomerAccessToken_whenCustomer_thenIncludeCustomerPrincipalAndRole() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789012345678901234567890123456789012345678901234567890123");
        JwtService jwtService = new JwtService(properties);

        String token = jwtService.generateCustomerAccessToken(
                "customer@example.com",
                UUID.randomUUID(),
                99L,
                AuthProvider.BASIC
        );

        Claims claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertThat(claims.get(JwtService.PRINCIPAL_TYPE_CLAIM)).isEqualTo(JwtService.PRINCIPAL_CUSTOMER);
        assertThat(jwtService.isCustomerPrincipal(claims)).isTrue();
        assertThat(jwtService.isDashboardPrincipal(claims)).isFalse();
        assertThat(jwtService.extractRoles(claims)).containsExactly("ROLE_CUSTOMER");
        assertThat(claims.get("userId")).isEqualTo(99);
        assertThat(claims.get("provider")).isEqualTo("BASIC");
        assertThat(claims.get("typ")).isEqualTo("access");
    }

    @Test
    void generateAccessToken_whenProviderIsNull_thenDefaultToBasic() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789012345678901234567890123456789012345678901234567890123");
        JwtService jwtService = new JwtService(properties);
        UserAccessProfile profile = new UserAccessProfile(
                CatalogPackages.FREE_PACKAGE,
                List.of(CatalogProducts.QR_CREATE),
                List.of(CatalogScopes.QR_CREATE_OWNER)
        );

        String token = jwtService.generateAccessToken(
                "user@example.com",
                UUID.randomUUID(),
                42L,
                UserRole.USER,
                null,
                profile
        );

        Claims claims = jwtService.parseValidAccessToken(token).orElseThrow();
        assertThat(claims.get("provider")).isEqualTo("BASIC");
    }
}
