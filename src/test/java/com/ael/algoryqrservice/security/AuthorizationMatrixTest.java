package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.authservice.application.auth.ActiveSessionRegistry;
import com.ael.algoryqrservice.authservice.security.JwtTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs real requests through the full security chain and checks who may reach what.
 * Each row is a rule that must keep holding as endpoints are added or refactored.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixTest {

    private static final long OWN_MERCHANT = 1L;
    private static final long OTHER_MERCHANT = 2L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenCodec codec;
    @MockitoBean
    private ActiveSessionRegistry activeSessions;

    @BeforeEach
    void sessionsAreOpen() {
        when(activeSessions.isActive(any())).thenReturn(true);
    }

    // ---- token helpers -------------------------------------------------------------------------

    private String merchantToken(long merchantId) {
        return codec.builder(JwtTokenCodec.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .subject("owner" + merchantId + "@example.com")
                .claim("subjectType", "MERCHANT")
                .claim("roles", List.of("ROLE_USER"))
                .claim("scopes", List.of())
                .claim("provider", "BASIC")
                .claim("merchantId", merchantId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
    }

    private String staffToken(long merchantId) {
        return codec.builder(JwtTokenCodec.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .subject("waiter")
                .claim("subjectType", "STAFF")
                .claim("roles", List.of("ROLE_WAITER"))
                .claim("staffId", 7L)
                .claim("branchId", 3L)
                .claim("merchantId", merchantId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
    }

    private String refreshShapedToken(long merchantId) {
        return codec.builder(JwtTokenCodec.TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .subject("owner" + merchantId + "@example.com")
                .claim("subjectType", "MERCHANT")
                .claim("merchantId", merchantId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
    }

    private int status(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static void assertDenied(int status) {
        assertThat(status).as("expected 401/403").isIn(401, 403);
    }

    // ---- ownership -----------------------------------------------------------------------------

    @Test
    void ownerEndpoint_withoutToken_isDenied() throws Exception {
        assertDenied(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), null));
    }

    @Test
    void ownerEndpoint_withOtherBusinessToken_isForbidden() throws Exception {
        assertThat(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), merchantToken(OTHER_MERCHANT)))
                .isEqualTo(403);
        assertThat(status(get("/api/v1/merchant/{id}/entitlements", OWN_MERCHANT), merchantToken(OTHER_MERCHANT)))
                .isEqualTo(403);
    }

    @Test
    void ownerEndpoint_withOwnToken_passesAuthorization() throws Exception {
        assertThat(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), merchantToken(OWN_MERCHANT)))
                .isNotIn(401, 403);
    }

    @Test
    void adminEndpoint_withOwnerToken_isForbidden() throws Exception {
        var request = post("/api/v1/merchant/{id}/package", OWN_MERCHANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageCode\":\"PRO\",\"periodDays\":30}");
        assertThat(status(request, merchantToken(OWN_MERCHANT))).isEqualTo(403);
    }

    @Test
    void ownerEndpoint_withStaffToken_isForbidden() throws Exception {
        assertThat(status(get("/api/v1/merchant/{id}/package", OWN_MERCHANT), staffToken(OWN_MERCHANT)))
                .isEqualTo(403);
    }

    // ---- token validity ------------------------------------------------------------------------

    @Test
    void refreshToken_usedAsBearer_isDenied() throws Exception {
        assertDenied(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), refreshShapedToken(OWN_MERCHANT)));
    }

    @Test
    void tokenForAnotherAudience_isDenied() throws Exception {
        JwtTokenCodec foreign = new JwtTokenCodec(
                "test-only-jwt-secret-0123456789abcdef0123456789abcdef", "algoryqr", "another-app");
        String token = foreign.builder(JwtTokenCodec.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .subject("owner1@example.com")
                .claim("subjectType", "MERCHANT")
                .claim("merchantId", OWN_MERCHANT)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
        assertDenied(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), token));
    }

    @Test
    void signedOutSession_isDeniedImmediately() throws Exception {
        when(activeSessions.isActive(any())).thenReturn(false);
        assertDenied(status(get("/api/v1/merchant/{id}/active-package", OWN_MERCHANT), merchantToken(OWN_MERCHANT)));
    }

    // ---- operational endpoints -----------------------------------------------------------------

    @Test
    void actuatorSecretsAndShutdown_areNotReachable() throws Exception {
        assertThat(status(get("/actuator/env"), null)).isIn(401, 403, 404);
        assertThat(status(get("/actuator/heapdump"), null)).isIn(401, 403, 404);
        assertThat(status(post("/actuator/shutdown"), null)).isIn(401, 403, 404);
        assertThat(status(get("/actuator/env"), merchantToken(OWN_MERCHANT))).isIn(401, 403, 404);
    }

    @Test
    void odealTestEndpoints_areOffByDefault() throws Exception {
        assertThat(status(get("/api/v1/integrations/odeal/test/units"), null)).isIn(401, 403, 404);
    }
}
