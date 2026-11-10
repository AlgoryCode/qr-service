package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ownership and role rules in one place, used from {@code @PreAuthorize}:
 *
 * <pre>
 * &#64;PreAuthorize("@access.ownsMerchant(#merchantId)")
 * &#64;PreAuthorize("@access.isPlatformAdmin()")
 * </pre>
 *
 * Keeping the rule out of each controller means a new endpoint cannot quietly forget it, and every
 * refusal is answered the same way (403 via {@code AccessDeniedAdvice}).
 */
@Component("access")
@RequiredArgsConstructor
public class AccessPolicy {

    private final SecurityUtils securityUtils;

    /** The signed-in owner or staff of {@code merchantId}; customers and other businesses are refused. */
    public boolean ownsMerchant(Long merchantId) {
        if (merchantId == null) {
            return false;
        }
        try {
            if (securityUtils.findCurrentCustomerId().isPresent()) {
                return false;
            }
            // The signed token's own merchant first: no database round trip for the common case.
            return securityUtils.matchesTokenMerchant(merchantId)
                    || merchantId.equals(securityUtils.getCurrentMerchantId());
        } catch (RuntimeException notSignedIn) {
            return false;
        }
    }

    /** Platform administrators (dashboard admin tokens). */
    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
