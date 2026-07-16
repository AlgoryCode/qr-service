package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.JwtProperties;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.ProductScope;
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
                PackageCode.PRO_PACKAGE,
                List.of(ProductCode.QR_CREATE, ProductCode.QR_MENU),
                List.of(ProductScope.QR_CREATE_OWNER, ProductScope.QR_MENU_OWNER)
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
        assertThat(claims.get("products", List.class)).containsExactly("QR_CREATE", "QR_MENU");
        assertThat(claims.get("scopes", List.class)).containsExactly("QR_CREATE_OWNER", "QR_MENU_OWNER");
        assertThat(claims.get("provider")).isEqualTo("GOOGLE");
    }

    @Test
    void generateAccessToken_whenProviderIsNull_thenDefaultToBasic() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789012345678901234567890123456789012345678901234567890123");
        JwtService jwtService = new JwtService(properties);
        UserAccessProfile profile = new UserAccessProfile(
                PackageCode.FREE_PACKAGE,
                List.of(ProductCode.QR_CREATE),
                List.of(ProductScope.QR_CREATE_OWNER)
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
