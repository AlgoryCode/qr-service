package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ProductScopeAspect {

    private final SecurityUtils securityUtils;
    private final EntitlementService entitlementService;

    @Before("@annotation(requiresProductScope)")
    public void requireScope(RequiresProductScope requiresProductScope) {
        Long userId = securityUtils.getCurrentUser().getId();
        entitlementService.requireScope(userId, requiresProductScope.value());
    }
}
