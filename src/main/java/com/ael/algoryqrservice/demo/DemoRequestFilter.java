package com.ael.algoryqrservice.demo;

import com.ael.algoryqrservice.security.JwtAccessPrincipal;
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
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DemoRequestFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final DemoSessionStore sessions;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            readBearer(request).flatMap(jwtService::parseValidAccessToken).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        if (!jwtService.extractPrincipalType(claims).equals(JwtService.PRINCIPAL_DEMO)) {
            return;
        }
        if (!sessions.isActive(jwtService.extractSessionId(claims))) {
            return;
        }
        String email = jwtService.extractEmail(claims);
        if (email == null || email.isBlank()) {
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        authentication.setDetails(new JwtAccessPrincipal(
                jwtService.extractUserId(claims),
                jwtService.extractScopes(claims),
                jwtService.extractProducts(claims),
                jwtService.extractActivePackage(claims),
                JwtService.PRINCIPAL_DEMO
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Optional<String> readBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(header.substring(7));
    }
}
