package com.ael.algoryqrservice.trial;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.trial.domain.TrialPolicy;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TrialUseCases {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final UserRepository userRepository;
    private final TrialLogRepository trialLogRepository;
    private final PlanPackageRepository packageRepository;
    private final TrialSnapshotQuery trialSnapshotQuery;
    private final FulfillmentGrantService fulfillmentGrantService;
    private final PurchaseLogService purchaseLogService;

    @Transactional
    public AdminUserDtos.ExtendTrialResponse extend(Long userId, int days) {
        validateDays(days);
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));

        TrialSnapshot snapshot = trialSnapshotQuery.forUser(userId);
        if (!TrialPolicy.canExtend(snapshot)) {
            throw new BadRequestException("Aktif ucretli paket varken deneme uzatilamaz");
        }

        TrialLog log = trialLogRepository.findByUserId(userId)
                .map(existing -> addDays(existing, days))
                .orElseGet(() -> startForAdmin(userId, days));

        PlanPackage planPackage = packageRepository.findByIdWithItems(log.getPackageId())
                .orElseThrow(() -> new BadRequestException("Ultimate deneme paketi bulunamadi veya aktif degil"));
        fulfillmentGrantService.grantOnboardingFulfillment(log, planPackage);
        fulfillmentGrantService.extendOnboardingPeriod(log);
        purchaseLogService.log(
                log.getId(),
                userId,
                PurchaseLogAction.TRIAL_EXTENDED,
                planPackage.getName() + " denemesi admin tarafindan " + days + " gun uzatildi"
        );

        return AdminUserDtos.ExtendTrialResponse.builder()
                .purchaseId(log.getId())
                .packageName(planPackage.getName())
                .expiresAt(log.getEndsAt())
                .daysAdded(days)
                .build();
    }

    @Transactional
    public AdminUserDtos.EndTrialResponse end(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));

        TrialSnapshot snapshot = trialSnapshotQuery.forUser(userId);
        if (!TrialPolicy.canEnd(snapshot)) {
            throw new BadRequestException("Aktif deneme bulunamadi");
        }

        TrialLog log = trialLogRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Aktif deneme bulunamadi"));
        log.setEndsAt(AppTime.nowLocal());
        log.setStatus(TrialLogStatus.ENDED);
        trialLogRepository.save(log);
        fulfillmentGrantService.expireFulfillmentForTrialLog(log.getId());

        return AdminUserDtos.EndTrialResponse.builder()
                .purchaseId(log.getId())
                .packageName(log.getPackageCode())
                .expiresAt(log.getEndsAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TrialSnapshot snapshot(Long userId) {
        return trialSnapshotQuery.forUser(userId);
    }

    private TrialLog addDays(TrialLog log, int days) {
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime base = log.isActiveAt(now) ? log.getEndsAt() : now;
        log.setStatus(TrialLogStatus.ACTIVE);
        log.setEndsAt(base.plusDays(days));
        if (log.getStartedAt() == null) {
            log.setStartedAt(now);
        }
        log.setDurationDays(log.getDurationDays() + days);
        return trialLogRepository.save(log);
    }

    private TrialLog startForAdmin(Long userId, int days) {
        PlanPackage trialPackage = packageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .filter(planPackage -> planPackage.isActive() && !planPackage.isSystemManaged())
                .orElseThrow(() -> new BadRequestException("Ultimate deneme paketi bulunamadi veya aktif degil"));
        LocalDateTime now = AppTime.nowLocal();
        return trialLogRepository.save(TrialLog.builder()
                .userId(userId)
                .packageId(trialPackage.getId())
                .packageCode(trialPackage.getCode())
                .startedAt(now)
                .endsAt(now.plusDays(days))
                .durationDays(days)
                .status(TrialLogStatus.ACTIVE)
                .build());
    }

    private void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("days " + MIN_DAYS + " ile " + MAX_DAYS + " arasinda olmalidir");
        }
    }
}
