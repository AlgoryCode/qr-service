package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.dto.AccessSessionResponse;

public final class AccessSessionMapper {

    public static final String PRODUCT_NOT_IN_PACKAGE = "PRODUCT_NOT_IN_PACKAGE";

    private AccessSessionMapper() {
    }

    public static AccessSessionResponse toResponse(AccessSession session) {
        return toResponse(session, null);
    }

    public static AccessSessionResponse toResponse(AccessSession session, String code) {
        return new AccessSessionResponse(
                session.decision(),
                session.packageCode(),
                session.endsAt(),
                session.debtDueAt(),
                session.messageKey(),
                code
        );
    }

    public static AccessSessionResponse productNotInPackage(AccessSession session) {
        return new AccessSessionResponse(
                session.decision(),
                session.packageCode(),
                session.endsAt(),
                session.debtDueAt(),
                PRODUCT_NOT_IN_PACKAGE,
                PRODUCT_NOT_IN_PACKAGE
        );
    }
}
