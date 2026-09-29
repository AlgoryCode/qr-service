package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.access.OnboardingPackageService;
import com.ael.algoryqrservice.demoonboarding.DemoOnboardingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo hesap pipeline: paket → şube → menü → ürünler → tema → satış verisi.
 * (Hesap oluşturma authservice tarafında; bu servis qr tarafını sırayla çalıştırır.)
 */
@Service
@RequiredArgsConstructor
public class DemoProvisionPipeline {

    private final OnboardingPackageService onboardingPackageService;
    private final DemoStoreProvisioner demoStoreProvisioner;
    private final DemoOnboardingAssignmentRepository assignmentRepository;
    private final DemoProvisionLock demoProvisionLock;

    @Transactional
    public void provision(Long userId, String email, String displayName) {
        demoProvisionLock.runExclusive(userId, () -> {
            if (assignmentRepository.existsByUserId(userId)) {
                return;
            }
            assignTrialPackage(userId, email, displayName);
            if (assignmentRepository.existsByUserId(userId)) {
                return;
            }
            provisionStore(userId, displayName);
        });
    }

    /** 15 günlük Ultimate deneme paketi + fulfillment. */
    public void assignTrialPackage(Long userId, String email, String displayName) {
        onboardingPackageService.assignDemoTrialPackage(userId, email, displayName);
    }

    /** Şube, menü, classpath ürün kataloğu, tema, sentetik satışlar. */
    public void provisionStore(Long userId, String displayName) {
        demoStoreProvisioner.provisionStore(userId, displayName);
    }
}
