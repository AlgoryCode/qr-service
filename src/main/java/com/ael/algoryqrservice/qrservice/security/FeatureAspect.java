package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/** Enforces {@link RequiresFeature} against the merchant's entitlement snapshot. */
@Aspect
@Component
@RequiredArgsConstructor
public class FeatureAspect {

    private final SecurityUtils securityUtils;
    private final ProductUsageGateway productUsageGateway;

    @Before("@annotation(requiresFeature)")
    public void requireOnMethod(RequiresFeature requiresFeature) {
        enforce(requiresFeature.value());
    }

    @Before("@within(requiresFeature) && !@annotation(com.ael.algoryqrservice.qrservice.security.RequiresFeature)")
    public void requireOnClass(RequiresFeature requiresFeature) {
        enforce(requiresFeature.value());
    }

    private void enforce(FeatureKey feature) {
        productUsageGateway.allow(securityUtils.getCurrentMerchantId(), feature.name());
    }
}
