package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.ForbiddenException;
import com.ael.algoryqrservice.authservice.infrastructure.client.PackageState;
import com.ael.algoryqrservice.fulfillmentservice.application.service.MerchantLoginPackageLookup;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.qrservice.billing.service.MemberPackageAccessRepairService;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MerchantPackageGate {

    private static final Logger log = LoggerFactory.getLogger(MerchantPackageGate.class);

    private final MerchantLoginPackageLookup loginPackageLookup;
    private final MemberPackageAccessRepairService memberPackageAccessRepairService;

    @AuthTransactional(propagation = Propagation.NOT_SUPPORTED)
    public PackageState assertOpen(AuthSubject subject) {
        Long merchantId = merchantId(subject);
        if (merchantId == null) {
            return PackageState.none();
        }
        PackageState state = findPackageState(merchantId);
        if (state.inactive()) {
            throw new ForbiddenException("PACKAGE_INACTIVE", "Paket aktif değil");
        }
        return state;
    }

    private PackageState findPackageState(Long merchantId) {
        memberPackageAccessRepairService.repairIfMissingActivePackage(merchantId);
        try {
            Optional<ActivePackage> active = loginPackageLookup.findActive(merchantId);
            if (active.isEmpty()) {
                return PackageState.none();
            }
            ActivePackage pkg = active.get();
            if (pkg.status() != PackageStatus.ACTIVE) {
                return new PackageState("INACTIVE", pkg.packageCode(), formatPeriodEnd(pkg));
            }
            return new PackageState("ACTIVE", pkg.packageCode(), formatPeriodEnd(pkg));
        } catch (FulfillmentUnavailableException exception) {
            log.warn(
                    "Fulfillment unavailable during login gate merchantId={}: {}",
                    merchantId,
                    exception.getMessage()
            );
            return PackageState.none();
        } catch (RuntimeException exception) {
            log.warn(
                    "Fulfillment lookup failed during login gate merchantId={}: {}",
                    merchantId,
                    exception.getMessage()
            );
            return PackageState.none();
        }
    }

    private static String formatPeriodEnd(ActivePackage pkg) {
        return pkg.periodEnd() == null ? null : pkg.periodEnd().toString();
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
}
