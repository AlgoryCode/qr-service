package com.ael.algoryqrservice.qrservice.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The signed-in merchant's plan must include this feature, else the request is refused with
 * {@code NO_ACTIVE_PLAN} or {@code FEATURE_NOT_IN_PLAN} and what would unlock it. On a class it
 * covers every handler that does not carry its own.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey value();
}
