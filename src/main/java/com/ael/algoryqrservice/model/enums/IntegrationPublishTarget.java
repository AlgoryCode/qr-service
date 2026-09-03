package com.ael.algoryqrservice.model.enums;

import java.util.Set;

public final class IntegrationPublishTarget {

    public static final String INTERNAL_MENU = "INTERNAL_MENU";
    public static final String UBEREATS = "UBEREATS";
    public static final Set<String> ALL = Set.of(INTERNAL_MENU, UBEREATS);

    private IntegrationPublishTarget() {
    }
}
