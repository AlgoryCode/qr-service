package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.access.PackageProductCatalog;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.model.dto.AccessSessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ProductAccessGatewayFilter extends OncePerRequestFilter {

    static final String FEATURES_PREFIX = "/features/";

    private final SessionAccessService sessionAccessService;
    private final PackageProductCatalog packageProductCatalog;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains(FEATURES_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String productCode = productCodeFrom(request.getRequestURI());
        if (productCode == null || productCode.isBlank()) {
            writeForbidden(response, AccessSessionMapper.toResponse(
                    AccessSession.of(
                            com.ael.algoryqrservice.model.enums.AccessDecision.REQUIRE_PURCHASE,
                            null,
                            null,
                            null
                    ),
                    AccessSessionMapper.PRODUCT_NOT_IN_PACKAGE
            ));
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof JwtAccessPrincipal principal)
                || principal.userId() == null
                || principal.isCustomer()
                || principal.isWaiter()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), java.util.Map.of("message", "Erisim reddedildi"));
            return;
        }

        AccessSession session = sessionAccessService.resolve(principal.userId());
        if (!session.isAllow()) {
            writeForbidden(response, AccessSessionMapper.toResponse(session));
            return;
        }
        if (!packageProductCatalog.containsProduct(session.packageCode(), productCode)) {
            writeForbidden(response, AccessSessionMapper.productNotInPackage(session));
            return;
        }
        filterChain.doFilter(request, response);
    }

    static String productCodeFrom(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        int index = requestUri.indexOf(FEATURES_PREFIX);
        if (index < 0) {
            return null;
        }
        String remainder = requestUri.substring(index + FEATURES_PREFIX.length());
        int slash = remainder.indexOf('/');
        String code = slash < 0 ? remainder : remainder.substring(0, slash);
        return code.isBlank() ? null : code;
    }

    private void writeForbidden(HttpServletResponse response, AccessSessionResponse body) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
