package com.ael.algoryqrservice.qrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps pre-{@code /api/v1} paths working while clients (dashboard, staff site, print agent, webhooks,
 * payment callbacks, internal services) migrate. Legacy requests are rewritten in place to their
 * {@code /api/v1} equivalent before Spring Security and MVC see them, so security rules only need the new paths.
 * Also absorbs the older {@code /waiter*} aliases that used to live on the staff controllers.
 * Remove once access logs show no {@code [compat]} hits.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LegacyApiPathCompatFilter extends OncePerRequestFilter {

    static final String API_PREFIX = "/api/v1";

    /** Specific legacy prefixes, checked in order before the generic root list. */
    private static final List<Map.Entry<String, String>> RENAMED_PREFIXES = List.of(
            Map.entry("/menu/catalog", "/menu"),
            Map.entry("/waiter-panel", "/staff/panel"),
            Map.entry("/waiter/orders", "/staff/orders"),
            Map.entry("/waiter/kitchen", "/staff/kitchen"),
            Map.entry("/waiter/courier", "/staff/courier"),
            Map.entry("/waiter/commissions", "/staff/commissions"),
            Map.entry("/waiter/campaigns", "/staff/campaigns"),
            Map.entry("/waiter", "/staff/ops"),
            Map.entry("/packages", "/plan-packages"),
            Map.entry("/auth/email-verification", "/auth/email-verification")
    );

    /** First path segments of controllers that moved under {@code /api/v1} unchanged. */
    private static final Set<String> MOVED_ROOTS = Set.of(
            "access", "account", "accounting", "admin", "analytics", "billing-addresses", "branches",
            "campaign", "coupons", "customer", "dashboard", "features", "integrations", "internal",
            "menu", "menus", "merchant", "order-flows", "payments", "platform-feedback", "print-agent",
            "purchases", "staff", "stock", "store"
    );

    private static final int MAX_LOGGED_PATHS = 500;
    private final Set<String> loggedPaths = ConcurrentHashMap.newKeySet();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = request.getRequestURI();
        String path = uri != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : null;
        String rewritten = rewrite(path);
        if (rewritten == null) {
            filterChain.doFilter(request, response);
            return;
        }
        logOnce(request.getMethod(), path, rewritten);
        String servletPath = request.getServletPath();
        String rewrittenServletPath = rewrite(servletPath);
        filterChain.doFilter(new RewrittenPathRequest(
                request,
                contextPath + rewritten,
                rewrittenServletPath != null ? rewrittenServletPath : servletPath
        ), response);
    }

    /** Returns the {@code /api/v1} path for a legacy path, or {@code null} when the path is not legacy. */
    static String rewrite(String path) {
        if (path == null || path.isBlank() || path.startsWith(API_PREFIX + "/") || path.equals(API_PREFIX)) {
            return null;
        }
        for (Map.Entry<String, String> entry : RENAMED_PREFIXES) {
            if (hasPrefix(path, entry.getKey())) {
                return API_PREFIX + entry.getValue() + path.substring(entry.getKey().length());
            }
        }
        int end = path.indexOf('/', 1);
        String root = end < 0 ? path.substring(1) : path.substring(1, end);
        return MOVED_ROOTS.contains(root) ? API_PREFIX + path : null;
    }

    private static boolean hasPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void logOnce(String method, String path, String rewritten) {
        String key = method + " " + path.replaceAll("/\\d+(?=/|$)", "/{id}");
        if (loggedPaths.size() < MAX_LOGGED_PATHS && loggedPaths.add(key)) {
            log.warn("[compat] deprecated path {} {} — use {}", method, path, rewritten);
        }
    }

    private static final class RewrittenPathRequest extends HttpServletRequestWrapper {

        private final String requestUri;
        private final String servletPath;

        RewrittenPathRequest(HttpServletRequest request, String requestUri, String servletPath) {
            super(request);
            this.requestUri = requestUri;
            this.servletPath = servletPath;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer original = super.getRequestURL();
            String originalUri = super.getRequestURI();
            int index = original.lastIndexOf(originalUri);
            if (index < 0) {
                return original;
            }
            return new StringBuffer(original.substring(0, index)).append(requestUri);
        }
    }
}
