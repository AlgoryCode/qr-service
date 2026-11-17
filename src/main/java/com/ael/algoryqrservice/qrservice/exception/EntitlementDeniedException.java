package com.ael.algoryqrservice.qrservice.exception;

import java.util.List;

/**
 * Why a feature was refused, in one shape for every caller: {@code NO_ACTIVE_PLAN},
 * {@code FEATURE_NOT_IN_PLAN} or {@code LIMIT_REACHED}, with the numbers and what would unlock it.
 */
public class EntitlementDeniedException extends ForbiddenException {

    public static final String NO_ACTIVE_PLAN = "NO_ACTIVE_PLAN";
    public static final String FEATURE_NOT_IN_PLAN = "FEATURE_NOT_IN_PLAN";
    public static final String LIMIT_REACHED = "LIMIT_REACHED";

    /** A package or add-on that grants more of the feature. {@code kind} is PACKAGE or ADDON. */
    public record Upgrade(String kind, String code, String name, Integer limit, boolean unlimited) {
    }

    private final String feature;
    private final String packageCode;
    private final Long limit;
    private final Long used;
    private final List<Upgrade> upgrades;

    public EntitlementDeniedException(
            String code,
            String message,
            String feature,
            String packageCode,
            Long limit,
            Long used,
            List<Upgrade> upgrades
    ) {
        super(code, message);
        this.feature = feature;
        this.packageCode = packageCode;
        this.limit = limit;
        this.used = used;
        this.upgrades = upgrades == null ? List.of() : List.copyOf(upgrades);
    }

    public String feature() {
        return feature;
    }

    public String packageCode() {
        return packageCode;
    }

    public Long limit() {
        return limit;
    }

    public Long used() {
        return used;
    }

    public List<Upgrade> upgrades() {
        return upgrades;
    }
}
