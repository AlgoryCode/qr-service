package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ProductScopeAspect {

    private final SecurityUtils securityUtils;
    private final EntitlementService entitlementService;

    @Before("@annotation(requiresProductScope)")
    public void requireScope(RequiresProductScope requiresProductScope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getDetails() instanceof JwtAccessPrincipal principal
                && principal.hasScope(requiresProductScope.value())) {
            return;
        }
        Long userId = securityUtils.getCurrentUserId();
        entitlementService.requireScope(userId, requiresProductScope.value());
    }
}
