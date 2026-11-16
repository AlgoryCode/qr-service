package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.access.AccessSession;
import com.ael.algoryqrservice.qrservice.access.BillingState;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.account.dto.SessionEntitlementResponse;
import com.ael.algoryqrservice.qrservice.account.dto.SessionUserResponse;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class SessionContextAssembler {

    public SessionContextResponse paid(
            SessionUserResponse user,
            AccessSession session,
            ExternalActivePackageResponse activePackage,
            List<ExternalEntitlementResponse> entitlements
    ) {
        return response(new Parts(
                user,
                AccessDecision.ALLOW,
                session.billingState(),
                activePackage.packageCode(),
                activePackage.packageName(),
                activePackage.periodStart(),
                activePackage.periodEnd(),
                endOf(activePackage.periodEnd()),
                session.debtDueAt(),
                sorted(activePackage.products()),
                sorted(activePackage.scopes()),
                entitlements.stream().map(this::entitlement).toList()
        ));
    }

    public SessionContextResponse localAllow(SessionUserResponse user, AccessSession session) {
        return response(new Parts(
                user,
                session.decision(),
                session.billingState(),
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
                session.billingState(),
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
                parts.billingState(),
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
                remaining(entitlement),
                entitlement.source(),
                entitlement.expiresAt()
        );
    }

    private static Integer remaining(ExternalEntitlementResponse entitlement) {
        if (entitlement.unlimited()) {
            return null;
        }
        int limit = entitlement.quantity() == null ? 0 : entitlement.quantity();
        int used = entitlement.usedQuantity() == null ? 0 : entitlement.usedQuantity();
        return Math.max(0, limit - used);
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
            BillingState billingState,
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
