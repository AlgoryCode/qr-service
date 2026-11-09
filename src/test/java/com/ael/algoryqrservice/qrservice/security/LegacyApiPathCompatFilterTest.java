package com.ael.algoryqrservice.qrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LegacyApiPathCompatFilterTest {

    @Test
    void rewrite_whenMovedRoot_thenPrefixesApiV1() {
        assertThat(LegacyApiPathCompatFilter.rewrite("/staff/bills/5/payments/amount"))
                .isEqualTo("/api/v1/staff/bills/5/payments/amount");
        assertThat(LegacyApiPathCompatFilter.rewrite("/merchant/1/branches/2/tables"))
                .isEqualTo("/api/v1/merchant/1/branches/2/tables");
        assertThat(LegacyApiPathCompatFilter.rewrite("/payments/paytr/notification"))
                .isEqualTo("/api/v1/payments/paytr/notification");
        assertThat(LegacyApiPathCompatFilter.rewrite("/menu")).isEqualTo("/api/v1/menu");
        assertThat(LegacyApiPathCompatFilter.rewrite("/menus/3/ai-import")).isEqualTo("/api/v1/menus/3/ai-import");
    }

    @Test
    void rewrite_whenMenuCatalogPath_thenDropsCatalogSegment() {
        assertThat(LegacyApiPathCompatFilter.rewrite("/menu/catalog/products")).isEqualTo("/api/v1/menu/products");
        assertThat(LegacyApiPathCompatFilter.rewrite("/menu/catalog/categories/7/subs"))
                .isEqualTo("/api/v1/menu/categories/7/subs");
        assertThat(LegacyApiPathCompatFilter.rewrite("/menu/catalog/option-groups/3/assign"))
                .isEqualTo("/api/v1/menu/option-groups/3/assign");
    }

    @Test
    void rewrite_whenRenamedLegacyPrefix_thenMapsToNewName() {
        assertThat(LegacyApiPathCompatFilter.rewrite("/waiter-panel/staff")).isEqualTo("/api/v1/staff/panel/staff");
        assertThat(LegacyApiPathCompatFilter.rewrite("/waiter/orders/pending")).isEqualTo("/api/v1/staff/orders/pending");
        assertThat(LegacyApiPathCompatFilter.rewrite("/waiter/shifts/current")).isEqualTo("/api/v1/staff/ops/shifts/current");
        assertThat(LegacyApiPathCompatFilter.rewrite("/packages/7")).isEqualTo("/api/v1/plan-packages/7");
        assertThat(LegacyApiPathCompatFilter.rewrite("/auth/email-verification/verify"))
                .isEqualTo("/api/v1/auth/email-verification/verify");
    }

    @Test
    void rewrite_whenNotLegacy_thenNull() {
        assertThat(LegacyApiPathCompatFilter.rewrite("/api/v1/staff/orders")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/auth/login")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/google-auth/callback")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/oauth2/authorization/google")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/actuator/health")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/healthcheck")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/menuX")).isNull();
        assertThat(LegacyApiPathCompatFilter.rewrite("/storefront")).isNull();
    }

    @Test
    void doFilter_whenLegacyPath_thenForwardsWrappedRequest() throws Exception {
        LegacyApiPathCompatFilter filter = new LegacyApiPathCompatFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/orders/today");
        request.setServletPath("/staff/orders/today");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        HttpServletRequest forwarded = captor.getValue();
        assertThat(forwarded.getRequestURI()).isEqualTo("/api/v1/staff/orders/today");
        assertThat(forwarded.getServletPath()).isEqualTo("/api/v1/staff/orders/today");
        assertThat(forwarded.getRequestURL().toString()).isEqualTo("http://localhost/api/v1/staff/orders/today");
    }

    @Test
    void doFilter_whenNewPath_thenPassesOriginalRequest() throws Exception {
        LegacyApiPathCompatFilter filter = new LegacyApiPathCompatFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/staff/orders/today");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
