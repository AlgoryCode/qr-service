package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.MerchantLoginPackageLookup;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.qrservice.config.AppProperties;
import com.ael.algoryqrservice.qrservice.menu.service.MenuPublicAccessService;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rebuilds runtime fulfillment ({@code active_package} / entitlements) from an already-paid
 * {@link Purchase} when the purchase row exists but fulfillment state is missing (legacy cutover).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPackageAccessRepairService {

    private final MerchantLoginPackageLookup loginPackageLookup;
    private final MemberPackageAccessRepairWorker repairWorker;
    private final PurchaseRepository purchaseRepository;
    private final AppProperties appProperties;

    /**
     * Must not join auth (or other) transactions that already bound the shared JDBC connection.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = "authTransactionManager")
    public boolean repairIfMissingActivePackage(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return false;
        }
        if (loginPackageLookup.findActive(merchantId).isPresent()) {
            return false;
        }
        return repairWorker.repairFromLatestUsablePurchase(merchantId);
    }

    public int repairBatch(int batchSize) {
        int limit = batchSize > 0 ? batchSize : appProperties.getFulfillment().getRepairJobBatchSize();
        List<Long> userIds = purchaseRepository.findUserIdsNeedingFulfillmentRepair(
                LocalDateTime.now(),
                PageRequest.of(0, limit)
        );
        int repaired = 0;
        for (Long userId : userIds) {
            if (repairIfMissingActivePackage(userId)) {
                repaired++;
            }
        }
        return repaired;
    }

    @Component
    @RequiredArgsConstructor
    static class MemberPackageAccessRepairWorker {

        private static final String REPAIR_EVENT_PREFIX = "repair:purchase:";

        private final FulfillmentService fulfillmentService;
        private final PurchaseRepository purchaseRepository;
        private final MenuPublicAccessService menuPublicAccessService;

        @Lazy
        @Autowired
        private MemberPackageAccessRepairWorker self;

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        public boolean repairFromLatestUsablePurchase(Long merchantId) {
            Optional<Purchase> purchase = self.loadLatestUsablePurchase(merchantId);
            if (purchase.isEmpty()) {
                return false;
            }
            return self.applyFulfillmentRepair(purchase.get());
        }

        @Transactional(readOnly = true)
        Optional<Purchase> loadLatestUsablePurchase(Long merchantId) {
            return purchaseRepository.findByUserIdOrderByPurchasedAtDesc(merchantId).stream()
                    .filter(Purchase::isUsable)
                    .findFirst();
        }

        @Transactional(transactionManager = "fulfillmentTransactionManager")
        public boolean applyFulfillmentRepair(Purchase purchase) {
            if (purchase.getUserId() == null
                    || purchase.getPackageCode() == null
                    || purchase.getPackageCode().isBlank()) {
                return false;
            }
            LocalDate periodStart = resolvePeriodStart(purchase);
            LocalDate periodEnd = resolvePeriodEnd(purchase, periodStart);
            if (periodEnd == null) {
                return false;
            }
            try {
                fulfillmentService.fulfillPaidPackage(new FulfillmentCommand(
                        REPAIR_EVENT_PREFIX + purchase.getId(),
                        "package.repaired",
                        purchase.getId(),
                        purchase.getUserId(),
                        purchase.getPackageId(),
                        purchase.getPackageCode(),
                        purchase.getPaymentConversationId(),
                        purchase.getPaymentId(),
                        purchase.getSubscriptionId(),
                        1,
                        periodStart,
                        periodEnd,
                        purchase.getPrice() == null ? BigDecimal.ZERO : purchase.getPrice(),
                        purchase.getCurrency() == null ? "TRY" : purchase.getCurrency(),
                        Map.of(
                                "purchaseId", purchase.getId(),
                                "userId", purchase.getUserId(),
                                "packageCode", purchase.getPackageCode()
                        )
                ));
                menuPublicAccessService.syncForUser(purchase.getUserId());
                log.info(
                        "Repaired fulfillment from active purchase. merchantId={} purchaseId={} packageCode={}",
                        purchase.getUserId(),
                        purchase.getId(),
                        purchase.getPackageCode()
                );
                return true;
            } catch (RuntimeException exception) {
                log.warn(
                        "Fulfillment repair skipped. merchantId={} purchaseId={} reason={}",
                        purchase.getUserId(),
                        purchase.getId(),
                        exception.getMessage()
                );
                return false;
            }
        }

        private static LocalDate resolvePeriodStart(Purchase purchase) {
            if (purchase.getStartsAt() != null) {
                return purchase.getStartsAt().toLocalDate();
            }
            if (purchase.getPurchasedAt() != null) {
                return purchase.getPurchasedAt().toLocalDate();
            }
            return LocalDate.now(ZoneOffset.UTC);
        }

        private static LocalDate resolvePeriodEnd(Purchase purchase, LocalDate periodStart) {
            if (purchase.getExpiresAt() != null) {
                return purchase.getExpiresAt().toLocalDate();
            }
            return periodStart;
        }
    }

    @Component
    @Order(50)
    @RequiredArgsConstructor
    public static class StartupRepairRunner implements ApplicationRunner {

        private final MemberPackageAccessRepairService repairService;
        private final AppProperties appProperties;

        @Override
        public void run(ApplicationArguments args) {
            if (!appProperties.getFulfillment().isRepairJobEnabled()) {
                return;
            }
            int repaired = repairService.repairBatch(appProperties.getFulfillment().getRepairJobBatchSize());
            if (repaired > 0) {
                log.info("Startup fulfillment repair completed for {} merchant(s)", repaired);
            }
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ScheduledRepairJob {

        private final MemberPackageAccessRepairService repairService;
        private final AppProperties appProperties;

        @Scheduled(fixedDelayString = "${app.fulfillment.repair-job-fixed-delay-ms:900000}")
        public void runScheduledRepair() {
            if (!appProperties.getFulfillment().isRepairJobEnabled()) {
                return;
            }
            int repaired = repairService.repairBatch(appProperties.getFulfillment().getRepairJobBatchSize());
            if (repaired > 0) {
                log.info("Scheduled fulfillment repair completed for {} merchant(s)", repaired);
            }
        }
    }
}
