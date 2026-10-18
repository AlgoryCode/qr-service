package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.config.AuthTransactional;
import com.ael.algoryqrservice.fulfillmentservice.application.service.MerchantLoginPackageLookup;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.qrservice.billing.service.MemberPackageAccessRepairService;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentUnavailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoginPackageClaims {

    private static final Logger log = LoggerFactory.getLogger(LoginPackageClaims.class);

    private final MerchantLoginPackageLookup loginPackageLookup;
    private final EntitlementPort entitlementPort;
    private final MemberPackageAccessRepairService memberPackageAccessRepairService;

    @AuthTransactional(propagation = Propagation.NOT_SUPPORTED)
    public Snapshot resolve(AuthSubject subject) {
        Long merchantId = merchantId(subject);
        if (merchantId == null) {
            return Snapshot.none();
        }
        try {
            memberPackageAccessRepairService.repairIfMissingActivePackage(merchantId);
            Optional<ActivePackage> active = loginPackageLookup.findActive(merchantId);
            if (active.isEmpty()) {
                return Snapshot.none();
            }
            String packageCode = active.get().packageCode();
            if (packageCode == null || packageCode.isBlank()) {
                return Snapshot.none();
            }
            List<UserEntitlement> entitlements = entitlementPort.findActiveByMerchantId(merchantId);
            return new Snapshot(
                    packageCode,
                    distinctSorted(entitlements.stream().map(UserEntitlement::featureCode).toList()),
                    distinctSorted(entitlements.stream().map(UserEntitlement::scopeCode).toList())
            );
        } catch (FulfillmentUnavailableException exception) {
            log.warn(
                    "Fulfillment unavailable while resolving login package claims merchantId={}: {}",
                    merchantId,
                    exception.getMessage()
            );
            return Snapshot.none();
        } catch (RuntimeException exception) {
            log.warn(
                    "Fulfillment lookup failed while resolving login package claims merchantId={}: {}",
                    merchantId,
                    exception.getMessage()
            );
            return Snapshot.none();
        }
    }

    private static Long merchantId(AuthSubject subject) {
        if (subject == null) {
            return null;
        }
        if (AuthSubject.TYPE_ADMIN.equals(subject.subjectType()) || AuthSubject.TYPE_CUSTOMER.equals(subject.subjectType())) {
            return null;
        }
        if (subject.merchantId() != null) {
            return subject.merchantId();
        }
        if (AuthSubject.TYPE_MERCHANT.equals(subject.subjectType())) {
            return subject.subjectId();
        }
        return null;
    }

    private static List<String> distinctSorted(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    public record Snapshot(String activePackage, List<String> products, List<String> productScopes) {

        public Snapshot {
            products = products == null ? List.of() : List.copyOf(products);
            productScopes = productScopes == null ? List.of() : List.copyOf(productScopes);
        }

        public static Snapshot none() {
            return new Snapshot(null, List.of(), List.of());
        }

        public boolean present() {
            return activePackage != null && !activePackage.isBlank();
        }
    }
}
