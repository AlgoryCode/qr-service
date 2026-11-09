package com.ael.algoryqrservice.qrservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AccessGatewayFiltersTest {

    @Test
    void staffTokens_reachStaffOperationsButNotTheOwnerPanelOrOwnerApis() {
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/staff/bills/5/payments/items")).isTrue();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/staff/kitchen/orders")).isTrue();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/merchant/86/branches/40/tables")).isTrue();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/auth/access")).isTrue();

        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/staff/panel/staff")).isFalse();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/menu/30/categories")).isFalse();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/analytics/branch/40/summary")).isFalse();
        assertThat(StaffPathGatewayFilter.allowedForStaff("/api/v1/merchant/86/package")).isFalse();
    }

    @Test
    void internalApi_withoutConfiguredToken_refusesEveryCall() throws Exception {
        MockHttpServletResponse response = callInternal(new InternalApiTokenFilter(""), "anything");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void internalApi_requiresTheSharedToken() throws Exception {
        InternalApiTokenFilter filter = new InternalApiTokenFilter("s3cret-token");

        assertThat(callInternal(filter, null).getStatus()).isEqualTo(401);
        assertThat(callInternal(filter, "wrong").getStatus()).isEqualTo(401);
        assertThat(callInternal(filter, "s3cret-token").getStatus()).isEqualTo(200);
    }

    @Test
    void internalApi_leavesOtherPathsAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/staff/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalApiTokenFilter("").doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse callInternal(InternalApiTokenFilter filter, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/internal/staff/7");
        if (token != null) {
            request.addHeader(InternalApiTokenFilter.HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
