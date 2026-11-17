package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.adminservice.application.AdminAuditService;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminAuditLog;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal;
import com.ael.algoryqrservice.qrservice.util.ClientInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs after authentication on admin panel calls: refuses changes the caller's panel role does not
 * allow, and records every change (and every panel sign-in attempt) in the audit log.
 */
@Component
public class AdminRequestFilter extends OncePerRequestFilter {

    static final String AUTH_PREFIX = "/api/v1/dashboard/auth";
    static final int BODY_LIMIT = 4000;

    private static final Pattern SECRET_FIELDS = Pattern.compile(
            "(\"(?:password|newPassword|currentPassword|temporaryPassword|code|secret|token|refreshToken|mfaToken|"
                    + "accessToken|cardNumber|cvc|cvv)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTIFIER = Pattern.compile("\"(?:username|email)\"\\s*:\\s*\"([^\"]{1,255})\"");
    private static final Pattern ID_SEGMENT = Pattern.compile("^(\\d+|[0-9a-fA-F-]{32,36}|cnv-[\\w-]+)$");

    private final ObjectProvider<AdminAuditService> auditService;

    public AdminRequestFilter(ObjectProvider<AdminAuditService> auditService) {
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || !(path.startsWith(AdminPermissions.ADMIN_PREFIX) || path.startsWith(AUTH_PREFIX))
                || (AdminPermissions.isRead(request.getMethod()) && !path.startsWith(AdminPermissions.ADMIN_PREFIX))
                || path.equals(AUTH_PREFIX + "/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<DashboardRole> role = dashboardRole(auth);

        if (path.startsWith(AdminPermissions.ADMIN_PREFIX) && role.isPresent()
                && !AdminPermissions.allowed(role.get(), request.getMethod(), path)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"ADMIN_PERMISSION_DENIED\",\"message\":\"Bu işlem için panel rolünüzün yetkisi yok\"}");
            audit(request, null, auth, role.orElse(null), HttpServletResponse.SC_FORBIDDEN, "DENIED");
            return;
        }
        if (AdminPermissions.isRead(request.getMethod())) {
            // Reads are checked above but not recorded.
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, BODY_LIMIT * 2);
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(wrapped, response);
            status = response.getStatus();
        } finally {
            audit(request, wrapped, auth, role.orElse(null), status, status < 400 ? "SUCCESS" : status == 403 ? "DENIED" : "FAILED");
        }
    }

    private void audit(HttpServletRequest request, ContentCachingRequestWrapper wrapped, Authentication auth,
                       DashboardRole role, int status, String outcome) {
        AdminAuditService service = auditService.getIfAvailable();
        if (service == null) {
            return;
        }
        String path = request.getRequestURI();
        String body = wrapped == null ? null : maskedBody(wrapped.getContentAsByteArray());
        Long actorId = null;
        String actorName = null;
        if (auth != null && auth.getDetails() instanceof JwtAccessPrincipal principal && principal.dashboardUserId() != null) {
            actorId = principal.dashboardUserId();
            actorName = auth.getName();
        } else if (body != null) {
            Matcher m = IDENTIFIER.matcher(body);
            actorName = m.find() ? m.group(1) : null;
        }
        String relative = path.startsWith(AdminPermissions.ADMIN_PREFIX)
                ? path.substring(AdminPermissions.ADMIN_PREFIX.length())
                : path.substring("/api/v1".length());
        String[] segments = Arrays.stream(relative.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
        ClientInfo client = ClientInfo.from(request);
        service.record(AdminAuditLog.builder()
                .actorId(actorId)
                .actorName(actorName)
                .actorRole(role == null ? null : role.name())
                .method(request.getMethod())
                .path(truncate(path, 512))
                .action(truncate(request.getMethod() + " " + action(segments), 128))
                .targetType(targetType(segments))
                .targetId(targetId(segments))
                .status(status)
                .outcome(outcome)
                .ipAddress(truncate(client.ipAddress(), 64))
                .userAgent(truncate(client.userAgent(), 512))
                .requestBody(body)
                .build());
    }

    static Optional<DashboardRole> dashboardRole(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof JwtAccessPrincipal principal) || principal.dashboardUserId() == null) {
            return Optional.empty();
        }
        String prefix = "ROLE_DASHBOARD_";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(prefix))
                .map(a -> a.substring(prefix.length()))
                .flatMap(name -> Arrays.stream(DashboardRole.values()).filter(r -> r.name().equals(name)))
                .findFirst()
                // Tokens issued before roles existed belonged to the only role there was.
                .or(() -> Optional.of(DashboardRole.ADMIN));
    }

    static String maskedBody(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        String raw = new String(content, StandardCharsets.UTF_8);
        return truncate(SECRET_FIELDS.matcher(raw).replaceAll("$1\"***\""), BODY_LIMIT);
    }

    static String action(String[] segments) {
        StringBuilder out = new StringBuilder();
        for (String segment : segments) {
            out.append('/').append(ID_SEGMENT.matcher(segment).matches() ? "{id}" : segment);
        }
        return out.isEmpty() ? "/" : out.toString();
    }

    static String targetType(String[] segments) {
        if (segments.length == 0) {
            return null;
        }
        if (segments.length > 1 && ("billing".equals(segments[0]) || "auth".equals(segments[1]))) {
            return segments[1];
        }
        return segments[0];
    }

    static String targetId(String[] segments) {
        return Arrays.stream(segments).filter(s -> ID_SEGMENT.matcher(s).matches()).findFirst().orElse(null);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
