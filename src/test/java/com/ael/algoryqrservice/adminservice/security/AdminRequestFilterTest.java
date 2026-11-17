package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.adminservice.application.AdminAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRequestFilterTest {

    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminRequestFilter filter;

    @SuppressWarnings("unchecked")
    AdminRequestFilterTest() {
        ObjectProvider<AdminAuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(audit);
        filter = new AdminRequestFilter(provider);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("a@x.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_DASHBOARD_" + role)));
        auth.setDetails(new com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal(
                null, null, null, 7L, List.of(), List.of(), null, "DASHBOARD", null));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletResponse run(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void viewerCannotReadAdminOnlyAreas() throws Exception {
        signIn("VIEWER");
        assertThat(run("GET", "/api/v1/admin/audit-log").getStatus()).isEqualTo(403);
        verify(audit).record(any());
    }

    @Test
    void readsThatAreAllowedPassWithoutAnAuditEntry() throws Exception {
        signIn("VIEWER");
        assertThat(run("GET", "/api/v1/admin/payments").getStatus()).isEqualTo(200);
        verify(audit, never()).record(any());
    }

    @Test
    void deniedWritesAreRecorded() throws Exception {
        signIn("SUPPORT");
        MockHttpServletResponse response = run("POST", "/api/v1/admin/coupons");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ADMIN_PERMISSION_DENIED");
        verify(audit).record(any());
    }

    @Test
    void allowedWritesAreRecorded() throws Exception {
        signIn("FINANCE");
        assertThat(run("POST", "/api/v1/admin/coupons").getStatus()).isEqualTo(200);
        verify(audit).record(any());
    }
}
