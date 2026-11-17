package com.ael.algoryqrservice.adminservice.security;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which admin panel role may call which /api/v1/admin endpoint. Reads are open to every role except
 * on admin-only screens; changes need a role listed for the area. The super admin may do everything.
 */
public final class AdminPermissions {

    public static final String ADMIN_PREFIX = "/api/v1/admin";

    private record Rule(Pattern path, boolean adminOnlyRead, Set<DashboardRole> writers) {
    }

    private static final Set<DashboardRole> FINANCE = EnumSet.of(DashboardRole.FINANCE);
    private static final Set<DashboardRole> SUPPORT = EnumSet.of(DashboardRole.SUPPORT);
    private static final Set<DashboardRole> FINANCE_AND_SUPPORT = EnumSet.of(DashboardRole.FINANCE, DashboardRole.SUPPORT);

    /** First match wins; paths are relative to {@link #ADMIN_PREFIX}. */
    private static final List<Rule> RULES = List.of(
            rule("^/(dashboard-users|audit-log|system)(/|$)", true, EnumSet.noneOf(DashboardRole.class)),
            rule("^/users/\\d+/(package|trial|grants|modules)(/|$)", false, FINANCE),
            rule("^/billing/(collections|reminders)(/|$)", false, FINANCE_AND_SUPPORT),
            rule("^/(payments|purchases|billing|coupons|products|packages|catalog-codes|invoices|campaigns|"
                    + "price-changes|revenue|exports|showcase|features)(/|$)", false, FINANCE),
            rule("^/(users|members|platform-feedbacks|support)(/|$)", false, SUPPORT),
            rule("^/(announcements|email-templates|faq|themes|merchants/\\d+/themes)(/|$)", false, SUPPORT)
    );

    private AdminPermissions() {
    }

    private static Rule rule(String regex, boolean adminOnlyRead, Set<DashboardRole> writers) {
        return new Rule(Pattern.compile(regex), adminOnlyRead, writers);
    }

    public static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    /** Whether {@code role} may call {@code method path}; {@code path} is the full request path. */
    public static boolean allowed(DashboardRole role, String method, String path) {
        if (role == DashboardRole.ADMIN) {
            return true;
        }
        if (path == null || !path.startsWith(ADMIN_PREFIX)) {
            return true;
        }
        String relative = path.substring(ADMIN_PREFIX.length());
        Rule rule = RULES.stream().filter(r -> r.path().matcher(relative).find()).findFirst().orElse(null);
        if (isRead(method)) {
            return rule == null || !rule.adminOnlyRead();
        }
        return role != null && rule != null && rule.writers().contains(role);
    }
}
