package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.qrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.qrservice.access.SessionAccessService;
import com.ael.algoryqrservice.qrservice.account.dto.AccessSessionResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanPackageResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PublicPackageCatalogService;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trial a new merchant starts from the panel's welcome wizard: one click, no card. A merchant
 * that ever had a package cannot start another trial.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {

    private final SecurityUtils securityUtils;
    private final FulfillmentServiceClient fulfillmentServiceClient;
    private final SubscriptionLifecycleService lifecycleService;
    private final SessionAccessService sessionAccessService;
    private final PublicPackageCatalogService packageCatalogService;

    /** The trial package on offer, or 204 when this merchant is no longer eligible. */
    @GetMapping("/package")
    public ResponseEntity<PlanPackageResponse> trialPackage() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        if (fulfillmentServiceClient.hasAnyPackage(merchantId)) {
            return ResponseEntity.noContent().build();
        }
        return packageCatalogService.findByCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .filter(PlanPackageResponse::isActive)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Starts the trial (idempotent) and returns the new access decision for the panel to follow. */
    @PostMapping("/package")
    public AccessSessionResponse startTrial(@RequestBody(required = false) StartTrialRequest request) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        boolean started = lifecycleService.startTrial(merchantId);
        log.info("onboarding_trial merchantId={} started={} businessType={} usagePurpose={}",
                merchantId,
                started,
                request == null ? null : request.businessType(),
                request == null ? null : request.usagePurpose());
        return AccessSessionMapper.toResponse(sessionAccessService.resolve(merchantId));
    }

    /** {@code packageId} is accepted for compatibility; the trial package is fixed. */
    public record StartTrialRequest(Long packageId, String businessType, String usagePurpose) {
    }
}
