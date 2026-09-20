package com.ael.algoryqrservice.print.security;

import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import com.ael.algoryqrservice.print.service.PrintAgentTokenService;
import com.ael.algoryqrservice.print.service.PrintDeviceLookupService;
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
public class PrintDeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Print-Device-Token";
    public static final String ROLE = "ROLE_PRINT_AGENT";

    private final PrintAgentTokenService tokenService;
    private final PrintDeviceLookupService deviceLookupService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String hash = tokenService.hashToken(token.trim());
            deviceLookupService.findEnabledByTokenHash(hash).ifPresent(this::authenticate);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(PrintAgentDevice device) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "print-device-" + device.getId(),
                null,
                List.of(new SimpleGrantedAuthority(ROLE))
        );
        auth.setDetails(new PrintDevicePrincipal(device.getId(), device.getOwnerUserId(), device.getBranchId()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
