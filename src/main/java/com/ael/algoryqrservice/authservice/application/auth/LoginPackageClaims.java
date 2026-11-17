package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.config.AuthTransactional;
import com.ael.algoryqrservice.fulfillmentservice.application.service.MerchantLoginPackageLookup;
import com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService;
import com.ael.algoryqrservice.qrservice.billing.service.MemberPackageAccessRepairService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * What an access token says about the merchant's plan: only the entitlement version ({@code ent_ver}).
 * The plan itself is never in the token, so it cannot go stale for the token's lifetime; every check
 * reads the entitlement snapshot, and a client compares versions to know when to reload.
 */
@Component
@RequiredArgsConstructor
public class LoginPackageClaims {

    private static final Logger log = LoggerFactory.getLogger(LoginPackageClaims.class);

    private final MerchantLoginPackageLookup loginPackageLookup;
    private final MemberPackageAccessRepairService memberPackageAccessRepairService;
    private final EntitlementSnapshotService snapshots;

    /** Joins the caller's transaction so a token issue never holds two pooled connections at once. */
    @AuthTransactional(propagation = Propagation.SUPPORTS)
    public Snapshot resolve(AuthSubject subject) {
        Long merchantId = merchantId(subject);
        if (merchantId == null) {
            return Snapshot.none();
        }
        try {
            if (loginPackageLookup.findActive(merchantId).isEmpty()) {
                // A member who paid but lost the package row gets it back on sign-in.
                memberPackageAccessRepairService.repairIfMissingActivePackage(merchantId);
            }
            return new Snapshot(snapshots.version(merchantId));
        } catch (RuntimeException exception) {
            log.warn("Entitlement version lookup failed at sign-in merchantId={}: {}", merchantId, exception.getMessage());
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

    /** {@code entitlementVersion} is null for subjects without a merchant. */
    public record Snapshot(Long entitlementVersion) {

        public static Snapshot none() {
            return new Snapshot(null);
        }

        public boolean present() {
            return entitlementVersion != null;
        }
    }
}
