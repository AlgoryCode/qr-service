package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.model.dto.SessionEntitlementResponse;
import com.ael.algoryqrservice.model.dto.SessionUserResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class SessionContextAssembler {

    public SessionContextResponse paid(
            SessionUserResponse user,
            ExternalActivePackageResponse activePackage,
            List<ExternalEntitlementResponse> entitlements
    ) {
        return response(new Parts(
                user,
                AccessDecision.ALLOW,
                activePackage.packageCode(),
                activePackage.packageName(),
                activePackage.periodStart(),
                activePackage.periodEnd(),
                endOf(activePackage.periodEnd()),
                null,
                sorted(activePackage.products()),
                sorted(activePackage.scopes()),
                entitlements.stream().map(this::entitlement).toList()
        ));
    }

    public SessionContextResponse localAllow(SessionUserResponse user, AccessSession session) {
        return response(new Parts(
                user,
                session.decision(),
                session.packageCode(),
                null,
                null,
                null,
                session.endsAt(),
                session.debtDueAt(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    public SessionContextResponse blocked(SessionUserResponse user, AccessSession session) {
        return response(new Parts(
                user,
                session.decision(),
                session.packageCode(),
                null,
                null,
                null,
                session.endsAt(),
                session.debtDueAt(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    private SessionContextResponse response(Parts parts) {
        return new SessionContextResponse(
                parts.user(),
                parts.decision(),
                parts.packageCode(),
                parts.packageName(),
                parts.periodStart(),
                parts.periodEnd(),
                parts.endsAt(),
                parts.debtDueAt(),
                AccessSession.messageKeyOf(parts.decision()),
                parts.products(),
                parts.scopes(),
                parts.entitlements()
        );
    }

    private SessionEntitlementResponse entitlement(ExternalEntitlementResponse entitlement) {
        return new SessionEntitlementResponse(
                entitlement.productCode() != null ? entitlement.productCode() : entitlement.featureCode(),
                entitlement.featureCode(),
                entitlement.scopeCode(),
                entitlement.quantity(),
                entitlement.unlimited(),
                entitlement.usedQuantity(),
                entitlement.source()
        );
    }

    private List<String> sorted(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private LocalDateTime endOf(LocalDate periodEnd) {
        if (periodEnd == null) {
            return null;
        }
        return periodEnd.atStartOfDay();
    }

    private record Parts(
            SessionUserResponse user,
            AccessDecision decision,
            String packageCode,
            String packageName,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDateTime endsAt,
            LocalDateTime debtDueAt,
            List<String> products,
            List<String> scopes,
            List<SessionEntitlementResponse> entitlements
    ) {
    }
}
