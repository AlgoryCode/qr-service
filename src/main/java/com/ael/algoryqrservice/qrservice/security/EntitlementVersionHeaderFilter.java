package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tells merchant clients the current entitlement version on every response
 * ({@value #HEADER}). The token carries the version it was issued at ({@code ent_ver}); a client
 * that sees a newer one here reloads the plan instead of waiting for the token to expire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntitlementVersionHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Entitlement-Version";

    private final EntitlementSnapshotService snapshots;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Long merchantId = merchantId();
        if (merchantId != null) {
            try {
                response.setHeader(HEADER, Long.toString(snapshots.version(merchantId)));
            } catch (RuntimeException exception) {
                log.debug("Entitlement version header skipped merchantId={}: {}", merchantId, exception.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private static Long merchantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof JwtAccessPrincipal principal)
                || principal.isCustomer()) {
            return null;
        }
        return principal.merchantId();
    }
}
