package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.access.PackageProductCatalog;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAccessGatewayFilterTest {

    private SessionAccessService sessionAccessService;
    private PackageProductCatalog catalog;
    private ObjectMapper objectMapper;
    private ProductAccessGatewayFilter filter;

    @BeforeEach
    void setUp() {
        sessionAccessService = mock(SessionAccessService.class);
        catalog = mock(PackageProductCatalog.class);
        objectMapper = new ObjectMapper();
        filter = new ProductAccessGatewayFilter(sessionAccessService, catalog, objectMapper);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productCodeFrom_whenFeaturesPrefix_thenReadFirstSegment() {
        assertThat(ProductAccessGatewayFilter.productCodeFrom("/features/QR_MENU/qr/create"))
                .isEqualTo("QR_MENU");
        assertThat(ProductAccessGatewayFilter.productCodeFrom("/api/features/QR_BRANCH/branches"))
                .isEqualTo("QR_BRANCH");
        assertThat(ProductAccessGatewayFilter.productCodeFrom("/branches")).isNull();
    }

    @Test
    void shouldNotFilter_whenOutsideFeaturesPrefix_thenSkip() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/qr/create");
        request.setRequestURI("/qr/create");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_whenFeaturesPrefix_thenApply() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/features/QR_MENU/qr/create");
        request.setRequestURI("/features/QR_MENU/qr/create");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilter_whenSessionNotAllow_thenForbiddenWithDecision() throws Exception {
        authenticateOwner(7L);
        when(sessionAccessService.resolve(7L)).thenReturn(
                AccessSession.of(AccessDecision.REQUIRE_PURCHASE, null, null, null)
        );
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request("/features/QR_MENU/qr/create"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = objectMapper.readValue(response.getContentAsByteArray(), java.util.Map.class);
        assertThat(body.get("decision")).isEqualTo("REQUIRE_PURCHASE");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilter_whenProductMissingFromPackage_thenForbidden() throws Exception {
        authenticateOwner(7L);
        when(sessionAccessService.resolve(7L)).thenReturn(
                AccessSession.of(AccessDecision.ALLOW, CatalogPackages.ULTIMATE_TRIAL_PACKAGE, null, null)
        );
        when(catalog.containsProduct(CatalogPackages.ULTIMATE_TRIAL_PACKAGE, "QR_MENU")).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request("/features/QR_MENU/qr/create"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = objectMapper.readValue(response.getContentAsByteArray(), java.util.Map.class);
        assertThat(body.get("messageKey")).isEqualTo(AccessSessionMapper.PRODUCT_NOT_IN_PACKAGE);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilter_whenAllowAndProductInPackage_thenContinue() throws Exception {
        authenticateOwner(7L);
        when(sessionAccessService.resolve(7L)).thenReturn(
                AccessSession.of(AccessDecision.ALLOW, CatalogPackages.ULTIMATE_TRIAL_PACKAGE, null, null)
        );
        when(catalog.containsProduct(CatalogPackages.ULTIMATE_TRIAL_PACKAGE, "QR_MENU")).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest servletRequest = request("/features/QR_MENU/qr/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(servletRequest, response, chain);

        verify(chain).doFilter(servletRequest, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static void authenticateOwner(Long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of());
        authentication.setDetails(new JwtAccessPrincipal(userId, List.of(), List.of(), null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
