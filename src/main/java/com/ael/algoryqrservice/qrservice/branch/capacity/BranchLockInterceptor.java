package com.ael.algoryqrservice.qrservice.branch.capacity;

import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

/**
 * Keeps branches locked by {@link CapacityOverageService} read-only for the merchant: any change
 * addressed to a locked branch, or to a menu of one, is refused with {@code BRANCH_LOCKED}. Reading
 * still works, as do deleting the branch, running reports and handling incoming orders, so guests of
 * a still-public menu are never left without service.
 */
@Component
@RequiredArgsConstructor
public class BranchLockInterceptor implements HandlerInterceptor {

    public static final String BRANCH_LOCKED = "BRANCH_LOCKED";

    private final JdbcTemplate jdbc;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!writes(request) || !merchantSession() || allowedWhileLocked(request)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) {
            return true;
        }
        if (locked(longOf(variables.get("branchId")), "select capacity_locked_at is not null from tbl_branch where id = ?")
                || locked(longOf(variables.get("menuId")), """
                        select b.capacity_locked_at is not null from tbl_menu m join tbl_branch b on b.id = m.branch_id
                        where m.menu_id = ?""")) {
            throw new ForbiddenException(BRANCH_LOCKED,
                    "Bu şube paket limitiniz aşıldığı için salt okunur. Şube seçiminizi değiştirin ya da paketinizi yükseltin.");
        }
        return true;
    }

    private static boolean writes(HttpServletRequest request) {
        String method = request.getMethod();
        return !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method) && !HttpMethod.OPTIONS.matches(method);
    }

    /** Deleting the branch itself, reports and the order flow stay open on a locked branch. */
    private static boolean allowedWhileLocked(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/orders/") || path.endsWith("/reports")) {
            return true;
        }
        return HttpMethod.DELETE.matches(request.getMethod()) && path.matches(".*/api/v1/branches/\\d+/?");
    }

    private static boolean merchantSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.merchantId() != null && !principal.isCustomer() && !principal.isWaiter();
    }

    private boolean locked(Long id, String sql) {
        if (id == null) {
            return false;
        }
        List<Boolean> rows = jdbc.queryForList(sql, Boolean.class, id);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    private static Long longOf(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException notAnId) {
            return null;
        }
    }
}
