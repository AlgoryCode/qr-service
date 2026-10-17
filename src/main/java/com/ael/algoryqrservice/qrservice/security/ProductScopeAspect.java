package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ProductScopeAspect {

    private final SecurityUtils securityUtils;
    private final ProductUsageGateway productUsageGateway;

    @Before("@annotation(requiresProductScope)")
    public void requireScopeOnMethod(RequiresProductScope requiresProductScope) {
        enforceScope(requiresProductScope.value());
    }

    @Before("@within(requiresProductScope) && !@annotation(com.ael.algoryqrservice.qrservice.security.RequiresProductScope)")
    public void requireScopeOnClass(RequiresProductScope requiresProductScope) {
        enforceScope(requiresProductScope.value());
    }

    private void enforceScope(String scopeCode) {
        productUsageGateway.allow(securityUtils.getCurrentMerchantId(), CatalogScopes.productCode(scopeCode));
    }
}
