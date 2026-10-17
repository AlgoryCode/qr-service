package com.ael.algoryqrservice.qrservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Temporary logging for dashboard-site migration from {@code /waiter*} to {@code /staff/*}.
 * Remove with alias {@code @RequestMapping} prefixes after dashboard-site prod verification.
 */
@Slf4j
@Component
public class DeprecatedStaffPathCompatFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && isDeprecatedStaffPath(uri)) {
            log.warn("[compat] deprecated path {} — use {}", uri, suggestedReplacement(uri));
        }
        filterChain.doFilter(request, response);
    }

    static boolean isDeprecatedStaffPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        if (requestUri.startsWith("/waiter-panel")) {
            return true;
        }
        if (requestUri.startsWith("/waiter/")) {
            return true;
        }
        return "/waiter".equals(requestUri);
    }

    static String suggestedReplacement(String requestUri) {
        if (requestUri.startsWith("/waiter-panel")) {
            return requestUri.replaceFirst("^/waiter-panel", "/staff/panel");
        }
        if (requestUri.startsWith("/waiter/orders")) {
            return requestUri.replaceFirst("^/waiter/orders", "/staff/orders");
        }
        if (requestUri.startsWith("/waiter/bills")) {
            return requestUri.replaceFirst("^/waiter/bills", "/staff/bills");
        }
        if (requestUri.startsWith("/waiter/kitchen")) {
            return requestUri.replaceFirst("^/waiter/kitchen", "/staff/kitchen");
        }
        if (requestUri.startsWith("/waiter/courier")) {
            return requestUri.replaceFirst("^/waiter/courier", "/staff/courier");
        }
        if (requestUri.startsWith("/waiter/commissions")) {
            return requestUri.replaceFirst("^/waiter/commissions", "/staff/commissions");
        }
        if (requestUri.startsWith("/waiter/campaigns")) {
            return requestUri.replaceFirst("^/waiter/campaigns", "/staff/campaigns");
        }
        if (requestUri.startsWith("/waiter/")) {
            return requestUri.replaceFirst("^/waiter", "/staff/ops");
        }
        return "/staff/...";
    }
}
