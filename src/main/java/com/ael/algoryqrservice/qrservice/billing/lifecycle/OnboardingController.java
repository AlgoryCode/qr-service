package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
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
 * There is no free trial any more: a new merchant picks a package and pays with a saved card in the shop. The
 * endpoints stay so an old panel gets a clear answer: nothing on offer, and starting one is refused.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {

    private final SecurityUtils securityUtils;

    /** No trial is on offer. */
    @GetMapping("/package")
    public ResponseEntity<Void> trialPackage() {
        return ResponseEntity.noContent().build();
    }

    /** Refused: a package is bought in the shop. */
    @PostMapping("/package")
    public ResponseEntity<Void> startTrial(@RequestBody(required = false) StartTrialRequest request) {
        log.info("onboarding_trial_refused merchantId={}", securityUtils.getCurrentMerchantId());
        throw new BadRequestException("Deneme paketi kaldırıldı. Paketinizi seçip ödeme yaparak başlayabilirsiniz.");
    }

    /** Kept so old clients still deserialize. */
    public record StartTrialRequest(Long packageId, String businessType, String usagePurpose) {
    }
}
