package com.ael.algoryqrservice.qrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Staff tokens carry their business's merchantId, and most owner endpoints only compare that id, so without this a
 * waiter, cook or courier could manage staff, menus, QR codes and reports like the owner. Staff tokens are kept to
 * what the staff apps use: the {@code /api/v1/staff/**} operations (not the owner's {@code /staff/panel}), the
 * branch table board and their own auth calls. Everything else answers 403.
 */
@Slf4j
@Component
public class StaffPathGatewayFilter extends OncePerRequestFilter {

    private static final String STAFF_PREFIX = "/api/v1/staff/";
    private static final String OWNER_PANEL_PREFIX = "/api/v1/staff/panel";
    private static final List<String> STAFF_OPEN_PREFIXES = List.of("/api/v1/auth/", "/api/v1/store/orders");
    private static final Pattern BRANCH_TABLES = Pattern.compile("^/api/v1/merchant/\\d+/branches/\\d+/tables$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (isStaff(SecurityContextHolder.getContext().getAuthentication()) && !allowedForStaff(path(request))) {
            log.warn("Staff token refused on owner path path={}", request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"STAFF_NOT_ALLOWED\",\"message\":\"Bu işlem işletme sahibine açık\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean allowedForStaff(String path) {
        if (path.startsWith(OWNER_PANEL_PREFIX)) {
            return false;
        }
        if (path.startsWith(STAFF_PREFIX) || BRANCH_TABLES.matcher(path).matches()) {
            return true;
        }
        return STAFF_OPEN_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication != null
                && authentication.getDetails() instanceof JwtAccessPrincipal principal
                && (principal.staffId() != null || principal.isWaiter());
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
