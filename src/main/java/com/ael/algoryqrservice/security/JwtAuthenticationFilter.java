package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.JwtService;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

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
            jwtService.parseValidAccessToken(jwt).ifPresent(this::setAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims) {
        String subject = jwtService.extractEmail(claims);
        if (subject == null || subject.isBlank()) {
            return;
        }

        String principalType = jwtService.extractPrincipalType(claims);
        String subjectType = jwtService.extractSubjectType(claims);
        Long merchantId = null;
        if (JwtService.SUBJECT_MERCHANT.equals(subjectType) || JwtService.SUBJECT_STAFF.equals(subjectType)) {
            merchantId = jwtService.extractPackageOwnerId(claims);
        }
        Long userId = merchantId != null ? merchantId : jwtService.extractUserId(claims);
        List<String> roles = jwtService.extractRoles(claims);
        List<String> scopes;
        List<String> products;
        String activePackage;
        Long branchId = null;
        if (JwtService.PRINCIPAL_CUSTOMER.equals(principalType)
                || JwtService.PRINCIPAL_WAITER.equals(principalType)) {
            scopes = List.of();
            products = List.of();
            activePackage = null;
            if (JwtService.PRINCIPAL_WAITER.equals(principalType)) {
                branchId = jwtService.extractBranchId(claims);
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
                userId,
                scopes,
                products,
                activePackage,
                principalType,
                branchId,
                merchantId
        ));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
