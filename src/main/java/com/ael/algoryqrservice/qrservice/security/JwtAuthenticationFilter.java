package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import com.ael.algoryqrservice.qrservice.service.DashboardSessionService;
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
    private final DashboardSessionService dashboardSessionService;

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
                    .ifPresent(this::setAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSessionActive(Claims claims) {
        if (!jwtService.isDashboardPrincipal(claims)) {
            return true;
        }
        UUID sessionId = jwtService.extractSessionId(claims);
        return dashboardSessionService.isSessionActive(sessionId);
    }

    private void setAuthentication(Claims claims) {
        String subject = jwtService.extractEmail(claims);
        if (subject == null || subject.isBlank()) {
            return;
        }

        String principalType = jwtService.extractPrincipalType(claims);
        String subjectType = jwtService.extractSubjectType(claims);
        Long merchantId = jwtService.extractMerchantId(claims);
        Long staffId = jwtService.extractStaffId(claims);
        Long customerId = jwtService.extractCustomerId(claims);
        Long dashboardUserId = jwtService.extractDashboardUserId(claims);
        Long branchId = null;

        if (JwtService.SUBJECT_STAFF.equals(subjectType) || jwtService.isWaiterPrincipal(claims)) {
            principalType = JwtService.PRINCIPAL_WAITER;
            branchId = jwtService.extractBranchId(claims);
        } else if (JwtService.SUBJECT_MERCHANT.equals(subjectType)) {
            if (merchantId == null) {
                merchantId = jwtService.extractPackageOwnerId(claims);
            }
        } else if (jwtService.isCustomerPrincipal(claims)) {
            if (customerId == null) {
                customerId = jwtService.extractCustomerId(claims);
            }
        } else if (jwtService.isDashboardPrincipal(claims)) {
            if (dashboardUserId == null) {
                dashboardUserId = jwtService.extractDashboardUserId(claims);
            }
        } else if (merchantId == null && staffId == null && customerId == null && dashboardUserId == null) {
            merchantId = jwtService.extractMerchantId(claims);
        }

        List<String> roles = jwtService.extractRoles(claims);
        List<String> scopes;
        List<String> products;
        String activePackage;
        if (JwtService.PRINCIPAL_CUSTOMER.equals(principalType)
                || JwtService.PRINCIPAL_WAITER.equals(principalType)) {
            scopes = List.of();
            products = List.of();
            activePackage = null;
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
                staffId,
                merchantId,
                customerId,
                dashboardUserId,
                scopes,
                products,
                activePackage,
                principalType,
                branchId
        ));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
