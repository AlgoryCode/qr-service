package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepairFulfillmentJob {

    private final PurchaseRepository purchaseRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentMigrationService fulfillmentMigrationService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelay = 600_000)
    public void repairOrphanedFulfillments() {
        if (!appProperties.getFulfillment().isRepairJobEnabled()) {
            return;
        }
        log.debug("RepairFulfillmentJob running");
        List<Purchase> orphans = findOrphanedPurchases();
        int count = 0;
        for (Purchase purchase : orphans) {
            try {
                repairSingle(purchase);
                count++;
            } catch (Exception e) {
                log.error("Repair failed for purchaseId={}: {}", purchase.getId(), e.getMessage(), e);
            }
        }
        if (count > 0) {
            log.info("RepairFulfillmentJob: repaired {} orphaned purchases", count);
        }
    }

    @Transactional
    public void repairForPurchase(Long purchaseId) {
        purchaseRepository.findById(purchaseId).ifPresent(this::repairSingle);
    }

    @Transactional
    public void expireOrphanedFulfillments() {
        List<Long> expiredUserIds = grantFulfillmentRepository.findDistinctUserIdsWithExpiredFulfillments().stream()
                .limit(appProperties.getFulfillment().getRepairJobBatchSize())
                .toList();
        for (Long userId : expiredUserIds) {
            List<GrantFulfillment> expired = grantFulfillmentRepository.findExpiredActiveByUserId(userId);
            for (GrantFulfillment f : expired) {
                f.setStatus(GrantFulfillmentStatus.EXPIRED);
                grantFulfillmentRepository.save(f);
            }
        }
    }

    private List<Purchase> findOrphanedPurchases() {
        return purchaseRepository.findActiveWithoutFulfillment(
                        PageRequest.of(0, appProperties.getFulfillment().getRepairJobBatchSize())
                ).stream()
                .filter(Purchase::isUsable)
                .toList();
    }

    private void repairSingle(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE || !purchase.isUsable()) {
            return;
        }
        fulfillmentMigrationService.backfillUser(purchase.getUserId());
    }
}
