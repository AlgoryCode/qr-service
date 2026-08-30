package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrialService {

    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository packageRepository;
    private final UserRepository userRepository;
    private final PackageEntitlementWriter entitlementWriter;
    private final PurchaseExpiryService purchaseExpiryService;
    private final PackageActivationService packageActivationService;
    private final UserTrialService userTrialService;
    private final PaymentServiceClient paymentServiceClient;
    private final BillingAddressService billingAddressService;
    private final PaymentRequestMapper paymentRequestMapper;
    private final AppProperties appProperties;

    @Transactional
    public TrialDtos.Status start(Long userId, Long packageId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Kullanici bulunamadi"));
        if (userTrialService.hasUsedTrial(user) || userTrialService.hasTrialPurchase(userId)) {
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        rejectIfHasUsablePaidPackage(userId);
        Long paymentMethodId = requireSavedCard(userId);

        PlanPackage planPackage = resolveTrialPackage(packageId);
        LocalDateTime startsAt = AppTime.nowLocal();
        Purchase purchase;
        try {
            purchase = purchaseRepository.saveAndFlush(Purchase.builder()
                    .userId(userId)
                    .packageId(planPackage.getId())
                    .packageCode(planPackage.getCode())
                    .packageName(planPackage.getName())
                    .price(BigDecimal.ZERO)
                    .currency(planPackage.getCurrency())
                    .purchaseType(PurchaseType.TRIAL)
                    .paymentStyle(PaymentStyle.SUBSCRIPTION)
                    .paymentMethodId(paymentMethodId)
                    .billingPeriod(BillingPeriod.MONTHLY)
                    .billingIntervalMonths(BillingPeriod.MONTHLY.intervalMonths())
                    .status(PurchaseStatus.ACTIVE)
                    .startsAt(startsAt)
                    .expiresAt(startsAt.plusDays(resolvedTrialDays(planPackage)))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Deneme hakki daha once kullanilmis");
        }
        packageActivationService.activatePurchasedPackage(purchase);
        bootstrapTrialSubscription(user, purchase, planPackage);
        for (PlanPackageItem item : planPackage.getItems()) {
            entitlementWriter.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
        return statusOf(purchase);
    }

    @Transactional
    public TrialDtos.Status startDigitalMenuPro(Long userId) {
        PlanPackage planPackage = packageRepository
                .findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc()
                .flatMap(existing -> packageRepository.findByIdWithItems(existing.getId()))
                .filter(pkg -> !pkg.isSystemManaged())
                .orElseThrow(() -> new BadRequestException("Deneme icin uygun paket bulunamadi"));
        return start(userId, planPackage.getId());
    }

    @Transactional
    public TrialDtos.Status status(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        Purchase purchase = purchaseRepository
                .findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(userId, PurchaseType.TRIAL)
                .orElse(null);
        if (purchase == null) {
            if (user != null && userTrialService.hasUsedTrial(user)) {
                return usedUnavailableStatus(user);
            }
            return availableStatus();
        }
        if (purchase.getStatus() == PurchaseStatus.ACTIVE && purchase.isExpiredByDate()) {
            purchaseExpiryService.expire(purchase);
            packageActivationService.ensureSubscriptionState(userId);
            user = userRepository.findById(userId).orElse(user);
            return usedUnavailableStatus(user);
        }
        if (purchase.getStatus() != PurchaseStatus.ACTIVE) {
            user = userRepository.findById(userId).orElse(user);
            return usedUnavailableStatus(user);
        }
        if (user != null && purchase.getPaymentMethodId() != null && purchase.getSubscriptionId() == null) {
            PlanPackage planPackage = purchase.getPackageId() == null
                    ? null
                    : packageRepository.findByIdWithItems(purchase.getPackageId()).orElse(null);
            if (planPackage != null) {
                bootstrapTrialSubscription(user, purchase, planPackage);
            }
        }
        return statusOf(purchase);
    }

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> listEligiblePackages() {
        return packageRepository.findByTrialEligibleTrueAndActiveTrueAndSystemManagedFalseOrderByPriorityDesc()
                .stream()
                .map(this::toEligiblePackageResponse)
                .toList();
    }

    private PlanPackage resolveTrialPackage(Long packageId) {
        if (packageId == null) {
            throw new BadRequestException("Paket id zorunludur");
        }
        PlanPackage planPackage = packageRepository.findByIdWithItems(packageId)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadi: " + packageId));
        if (!planPackage.isActive() || !planPackage.isTrialEligible() || planPackage.isSystemManaged()) {
            throw new BadRequestException("Bu paket deneme icin uygun degil");
        }
        if (planPackage.getItems() == null || planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Deneme paketinde urun bulunamadi");
        }
        resolvedTrialDays(planPackage);
        return planPackage;
    }

    private int resolvedTrialDays(PlanPackage planPackage) {
        Integer trialDays = planPackage.getTrialDays();
        if (trialDays == null || trialDays < 1) {
            throw new BadRequestException("Deneme paketi icin trialDays zorunludur");
        }
        int maxTrialDays = Math.min(
                planPackage.getValidityDays() == null ? 30 : planPackage.getValidityDays(),
                30
        );
        if (trialDays > maxTrialDays) {
            throw new BadRequestException("trialDays 1 ile " + maxTrialDays + " arasinda olmalidir");
        }
        return trialDays;
    }

    private Long requireSavedCard(Long userId) {
        List<BillingPaymentDtos.PaymentMethod> methods = paymentServiceClient.getPaymentMethods(userId);
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Deneme baslatmak icin kayitli kredi karti zorunludur");
        }
        String rawId = methods.getFirst().id();
        if (rawId == null || rawId.isBlank()) {
            throw new BadRequestException("Deneme baslatmak icin kayitli kredi karti zorunludur");
        }
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Kayitli kart kimligi gecersiz");
        }
    }

    private void bootstrapTrialSubscription(User user, Purchase purchase, PlanPackage planPackage) {
        if (purchase.getPaymentMethodId() == null) {
            return;
        }
        BigDecimal amount = planPackage.effectiveMonthlyPrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = planPackage.getPrice();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Trial subscription bootstrap skipped; package price missing. purchaseId={}", purchase.getId());
            return;
        }
        BillingSnapshot billingSnapshot = billingAddressService.resolveDefaultSnapshot(user.getId());
        PaymentCardVerificationRequest identity = paymentRequestMapper.toCardVerificationRequest(
                user,
                billingSnapshot,
                "127.0.0.1",
                appProperties,
                "trialboot" + purchase.getId()
        );
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("trialConversion", true);
        sourceMetadata.put("paymentStyle", PaymentStyle.SUBSCRIPTION.name());
        try {
            var subscription = paymentServiceClient.bootstrapSubscription(
                    user.getId(),
                    appProperties.getServiceName(),
                    String.valueOf(purchase.getId()),
                    "trialboot" + purchase.getId(),
                    amount,
                    planPackage.getCurrency() == null ? purchase.getCurrency() : planPackage.getCurrency(),
                    purchase.getBillingIntervalMonths() == null ? 1 : purchase.getBillingIntervalMonths(),
                    purchase.getPaymentMethodId(),
                    purchase.getExpiresAt(),
                    sourceMetadata,
                    identity.getBuyer(),
                    identity.getShippingAddress(),
                    identity.getBillingAddress()
            );
            if (subscription != null && subscription.id() != null) {
                purchase.setSubscriptionId(subscription.id());
                purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                purchaseRepository.save(purchase);
            }
        } catch (PaymentServiceException exception) {
            log.error(
                    "Trial subscription bootstrap failed. purchaseId={} userId={}",
                    purchase.getId(),
                    user.getId(),
                    exception
            );
        }
    }

    private void rejectIfHasUsablePaidPackage(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        boolean hasPaid = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .anyMatch(purchase -> purchase.isUsable()
                        && purchase.getPurchaseType() == PurchaseType.PAID);
        if (hasPaid) {
            throw new BadRequestException("Aktif ucretli paket varken deneme baslatilamaz");
        }
    }

    private TrialDtos.Status availableStatus() {
        return new TrialDtos.Status(
                TrialDtos.Lifecycle.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TrialDtos.Status usedUnavailableStatus(User user) {
        return new TrialDtos.Status(
                TrialDtos.Lifecycle.TRIAL_EXPIRED,
                null,
                null,
                user != null ? user.getTrialEndDate() : null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TrialDtos.Status statusOf(Purchase purchase) {
        TrialDtos.Lifecycle lifecycle = purchase.getStatus() == PurchaseStatus.ACTIVE && !purchase.isExpiredByDate()
                ? TrialDtos.Lifecycle.ACTIVE
                : TrialDtos.Lifecycle.TRIAL_EXPIRED;

        Integer daysUntilExpiry = null;
        if (purchase.getExpiresAt() != null && lifecycle == TrialDtos.Lifecycle.ACTIVE) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(
                    AppTime.nowLocal().toLocalDate(),
                    purchase.getExpiresAt().toLocalDate()
            );
            if (daysUntilExpiry < 0) {
                daysUntilExpiry = 0;
            }
        }

        PlanPackage planPackage = purchase.getPackageId() == null
                ? null
                : packageRepository.findById(purchase.getPackageId()).orElse(null);
        BigDecimal catalogPrice = planPackage != null ? planPackage.getPrice() : null;
        String currency = planPackage != null && planPackage.getCurrency() != null
                ? planPackage.getCurrency()
                : purchase.getCurrency();

        return new TrialDtos.Status(
                lifecycle,
                purchase.getId(),
                purchase.getStartsAt(),
                purchase.getExpiresAt(),
                purchase.getPackageId(),
                purchase.getPackageCode(),
                purchase.getPackageName(),
                daysUntilExpiry,
                catalogPrice,
                currency
        );
    }

    private PlanPackageResponse toEligiblePackageResponse(PlanPackage planPackage) {
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
                .trialDays(planPackage.getTrialDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .trialEligible(planPackage.isTrialEligible())
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
