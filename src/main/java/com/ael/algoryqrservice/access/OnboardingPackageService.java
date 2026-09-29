package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AccessSessionResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.BusinessType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.model.enums.UsagePurpose;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.demo.DemoTrialAccountSupport;
import com.ael.algoryqrservice.service.AuthLinkedUserProvisioner;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.stage.StageTrialFixtureService;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingPackageService {

    private final UserRepository userRepository;
    private final PlanPackageRepository packageRepository;
    private final TrialLogRepository trialLogRepository;
    private final SessionAccessService sessionAccessService;
    private final SessionAccessPolicy sessionAccessPolicy;
    private final FulfillmentGrantService fulfillmentGrantService;
    private final AuthLinkedUserProvisioner authLinkedUserProvisioner;
    private final StageTrialFixtureService stageTrialFixtureService;
    private final OnboardingPackageCatalog onboardingPackageCatalog;

    /** Demo: 15 gunluk {@code ULTIMATE_TRIAL_PACKAGE} (magaza adimlari {@link com.ael.algoryqrservice.demo.provision.DemoProvisionPipeline}). */
    @Transactional
    public AccessSessionResponse assignDemoTrialPackage(Long userId, String email, String displayName) {
        if (!DemoTrialAccountSupport.isDemoEmail(email)) {
            throw new BadRequestException("Gecersiz demo hesap e-postasi");
        }
        User user = authLinkedUserProvisioner.ensureUser(userId, email, displayName);
        authLinkedUserProvisioner.refreshDemoProfile(user, displayName);
        LocalDateTime now = AppTime.nowLocal();
        Optional<TrialLog> existing = trialLogRepository.findByUserId(userId);
        if (existing.isPresent()) {
            TrialLog log = sessionAccessService.expireDue(existing, now).orElseThrow();
            if (log.isActiveAt(now)) {
                return AccessSessionMapper.toResponse(sessionAccessService.resolve(userId));
            }
            throw new BadRequestException("Deneme suresi sona ermis");
        }
        return startDemoUltimateTrial(userId, user);
    }

    @Deprecated
    @Transactional
    public AccessSessionResponse startDemoForAuthLinkedUser(Long userId, String email, String displayName) {
        return assignDemoTrialPackage(userId, email, displayName);
    }

    @Transactional
    public AccessSessionResponse start(
            Long userId,
            Long packageId,
            BusinessType businessType,
            UsagePurpose usagePurpose
    ) {
        return start(userId, packageId, businessType, usagePurpose, false);
    }

    @Transactional
    public AccessSessionResponse start(
            Long userId,
            Long packageId,
            BusinessType businessType,
            UsagePurpose usagePurpose,
            boolean skipEmailVerification
    ) {
        if (businessType == null) {
            throw new BadRequestException("Isletme tipi zorunludur");
        }
        if (usagePurpose == null) {
            throw new BadRequestException("Kullanim amaci zorunludur");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Kullanici bulunamadi"));
        if (!skipEmailVerification && user.getProvider() == AuthProvider.BASIC && !user.isEmailVerified()) {
            throw new BadRequestException("Deneme baslatmak icin e-posta adresinizi dogrulamaniz gerekiyor");
        }

        LocalDateTime now = AppTime.nowLocal();
        if (!sessionAccessPolicy.withinOnboardingWindow(user.getCreatedAt(), now)) {
            throw new BadRequestException("Deneme penceresi kapandi");
        }
        if (trialLogRepository.existsByUserId(userId)) {
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        Purchase paid = sessionAccessService.governingPaid(userId).orElse(null);
        if (paid != null && paid.isUsable()) {
            throw new BadRequestException("Aktif ucretli paket varken deneme baslatilamaz");
        }

        user.setBusinessType(businessType);
        user.setUsagePurpose(usagePurpose);
        userRepository.save(user);

        PlanPackage planPackage = loadOnboardingPackage(packageId);
        LocalDateTime endsAt = now.plusDays(planPackage.getValidityDays());
        TrialLog trialLog;
        try {
            trialLog = trialLogRepository.saveAndFlush(TrialLog.builder()
                    .userId(userId)
                    .packageId(planPackage.getId())
                    .packageCode(planPackage.getCode())
                    .startedAt(now)
                    .endsAt(endsAt)
                    .durationDays(planPackage.getValidityDays())
                    .status(TrialLogStatus.ACTIVE)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        fulfillmentGrantService.grantOnboardingFulfillment(trialLog, planPackage);
        if (!skipEmailVerification) {
            stageTrialFixtureService.assign(userId);
        }
        return AccessSessionMapper.toResponse(sessionAccessService.resolve(userId));
    }

    private AccessSessionResponse startDemoUltimateTrial(Long userId, User user) {
        if (user.getBusinessType() == null) {
            user.setBusinessType(BusinessType.OTHER);
        }
        if (user.getUsagePurpose() == null) {
            user.setUsagePurpose(UsagePurpose.EXPLORE_ALL);
        }
        userRepository.save(user);

        Purchase paid = sessionAccessService.governingPaid(userId).orElse(null);
        if (paid != null && paid.isUsable()) {
            throw new BadRequestException("Aktif ucretli paket varken deneme baslatilamaz");
        }

        PlanPackage planPackage = loadOnboardingPackage(null);
        LocalDateTime now = AppTime.nowLocal();
        int trialDays = DemoTrialAccountSupport.TRIAL_DAYS;
        if (planPackage.getValidityDays() != null && planPackage.getValidityDays() > 0) {
            trialDays = planPackage.getValidityDays();
        }
        LocalDateTime endsAt = now.plusDays(trialDays);
        TrialLog trialLog;
        try {
            trialLog = trialLogRepository.saveAndFlush(TrialLog.builder()
                    .userId(userId)
                    .packageId(planPackage.getId())
                    .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                    .startedAt(now)
                    .endsAt(endsAt)
                    .durationDays(trialDays)
                    .status(TrialLogStatus.ACTIVE)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            TrialLog raced = trialLogRepository.findByUserId(userId).orElse(null);
            if (raced != null && raced.isActiveAt(now)) {
                fulfillmentGrantService.grantOnboardingFulfillment(raced, planPackage);
                return AccessSessionMapper.toResponse(sessionAccessService.resolve(userId));
            }
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        fulfillmentGrantService.grantOnboardingFulfillment(trialLog, planPackage);
        return AccessSessionMapper.toResponse(sessionAccessService.resolve(userId));
    }

    @Transactional
    public void completeDemoSetup(Long userId, BusinessType businessType, UsagePurpose usagePurpose) {
        if (businessType == null) {
            throw new BadRequestException("Isletme tipi zorunludur");
        }
        if (usagePurpose == null) {
            throw new BadRequestException("Kullanim amaci zorunludur");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Kullanici bulunamadi"));
        if (!DemoTrialAccountSupport.isDemoEmail(user.getEmail())) {
            throw new BadRequestException("Bu islem yalnizca deneme surumu hesaplari icin gecerlidir");
        }
        TrialLog trial = trialLogRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Aktif deneme kaydi bulunamadi"));
        if (trial.getStatus() != TrialLogStatus.ACTIVE) {
            throw new BadRequestException("Deneme suresi sona ermis");
        }
        LocalDateTime now = AppTime.nowLocal();
        if (trial.getEndsAt() != null && trial.getEndsAt().isBefore(now)) {
            throw new BadRequestException("Deneme suresi sona ermis");
        }
        user.setBusinessType(businessType);
        user.setUsagePurpose(usagePurpose);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PlanPackageResponse catalogPackage() {
        PlanPackage planPackage = packageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .filter(pkg -> pkg.isActive() && !pkg.isSystemManaged())
                .orElseThrow(() -> new BadRequestException("Onboarding paketi bulunamadi"));
        return toResponse(planPackage);
    }

    private PlanPackage loadOnboardingPackage(Long packageId) {
        PlanPackage planPackage = onboardingPackageCatalog.ensureDefined();
        if (!CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(planPackage.getCode())) {
            throw new BadRequestException("Onboarding paketi bulunamadi");
        }
        if (!planPackage.isActive() || planPackage.isPurchasable() || planPackage.isSystemManaged()) {
            throw new BadRequestException("Bu paket deneme icin uygun degil");
        }
        if (planPackage.getValidityDays() == null || planPackage.getValidityDays() < 1) {
            throw new BadRequestException("Deneme paketi icin validityDays en az 1 olmalidir");
        }
        if (planPackage.getItems() == null || planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Deneme paketinde urun bulunamadi");
        }
        if (packageId != null && !packageId.equals(planPackage.getId())) {
            throw new BadRequestException("Bu paket deneme icin uygun degil");
        }
        return planPackage;
    }

    private PlanPackageResponse toResponse(PlanPackage planPackage) {
        return PlanPackageResponse.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .description(planPackage.getDescription())
                .features(planPackage.getFeatures() == null ? List.of() : List.copyOf(planPackage.getFeatures()))
                .price(planPackage.getPrice())
                .monthlyDiscount(planPackage.getMonthlyDiscount())
                .yearlyPrice(planPackage.getYearlyPrice())
                .yearlyDiscount(planPackage.getYearlyDiscount())
                .effectiveMonthlyPrice(planPackage.effectiveMonthlyPrice())
                .effectiveYearlyPrice(planPackage.effectiveYearlyPrice())
                .currency(planPackage.getCurrency())
                .active(planPackage.isActive())
                .validityDays(planPackage.getValidityDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .items(planPackage.getItems() == null ? List.of() : planPackage.getItems().stream()
                        .map(item -> PlanPackageItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProduct().getId())
                                .productCode(item.getProduct().getCode())
                                .productName(item.getProduct().getName())
                                .quantity(item.getQuantity())
                                .unlimited(item.isUnlimited())
                                .build())
                        .toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }
}
