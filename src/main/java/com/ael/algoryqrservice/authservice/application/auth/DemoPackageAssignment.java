package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.PackageAlreadyAssignedException;
import com.ael.algoryqrservice.qrservice.billing.policy.PlanPackageAssignmentPolicy;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoPackageAssignment {

    private final FulfillmentService fulfillmentService;
    private final PlanPackageRepository planPackageRepository;
    private final PlanPackageAssignmentPolicy assignmentPolicy;

    public void assignIfAbsent(Long merchantId) {
        if (fulfillmentService.findActivePackage(merchantId).isPresent()) {
            return;
        }
        try {
            int periodDays = planPackageRepository.findByCode(DemoAccountSupport.DEMO_PACKAGE_CODE)
                    .map(plan -> assignmentPolicy.resolvePeriodDays(
                            plan,
                            null,
                            DemoAccountSupport.DEMO_PACKAGE_VALIDITY_DAYS
                    ))
                    .orElse(DemoAccountSupport.DEMO_PACKAGE_VALIDITY_DAYS);
            fulfillmentService.assignPackage(
                    merchantId,
                    DemoAccountSupport.DEMO_PACKAGE_CODE,
                    periodDays
            );
        } catch (PackageAlreadyAssignedException ignored) {
        }
    }
}
