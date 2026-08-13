package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.CustomerSessionService;
import com.ael.algoryqrservice.service.DashboardSessionService;
import com.ael.algoryqrservice.service.JwtService;
import com.ael.algoryqrservice.service.MenuWaiterSessionService;
import com.ael.algoryqrservice.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final DashboardSessionService dashboardSessionService;
    private final CustomerSessionService customerSessionService;
    private final MenuWaiterSessionService menuWaiterSessionService;
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parseValidAccessToken(jwt)
                    .filter(this::isSessionActive)
                    .ifPresent(claims -> setAuthentication(request, claims));
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSessionActive(Claims claims) {
        UUID sessionId = jwtService.extractSessionId(claims);
        if (jwtService.isDashboardPrincipal(claims)) {
            return dashboardSessionService.isSessionActive(sessionId);
        }
        if (jwtService.isCustomerPrincipal(claims)) {
            return customerSessionService.isSessionActive(sessionId);
        }
        if (jwtService.isWaiterPrincipal(claims)) {
            return menuWaiterSessionService.isSessionActive(sessionId);
        }
        if (accessTokenBlacklistService.isBlacklisted(sessionId)) {
            return false;
        }
        return sessionService.isSessionActive(sessionId);
    }

    private void setAuthentication(HttpServletRequest request, Claims claims) {
        String subject = jwtService.extractEmail(claims);
        if (subject == null || subject.isBlank()) {
            return;
        }

        String principalType = jwtService.extractPrincipalType(claims);
        List<String> roles = jwtService.extractRoles(claims);
        List<String> scopes;
        List<String> products;
        String activePackage;
        Long menuId = null;
        if (JwtService.PRINCIPAL_CUSTOMER.equals(principalType)
                || JwtService.PRINCIPAL_WAITER.equals(principalType)) {
            scopes = List.of();
            products = List.of();
            activePackage = null;
            if (JwtService.PRINCIPAL_WAITER.equals(principalType)) {
                menuId = jwtService.extractMenuId(claims);
            }
        } else {
            scopes = jwtService.extractScopes(claims);
            products = jwtService.extractProducts(claims);
            activePackage = jwtService.extractActivePackage(claims);
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                subject,
                null,
                roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
        authToken.setDetails(new JwtAccessPrincipal(
                jwtService.extractUserId(claims),
                scopes,
                products,
                activePackage,
                principalType,
                menuId
        ));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
