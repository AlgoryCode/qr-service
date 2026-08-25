package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PlanPackageRepository planPackageRepository;
    private final FulfillmentGrantService fulfillmentGrantService;
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
        return purchaseRepository.findByStatusAndExpiresAtBefore(PurchaseStatus.ACTIVE, java.time.LocalDateTime.now())
                .stream()
                .filter(p -> p.getPaymentId() != null)
                .filter(p -> grantFulfillmentRepository.findByPurchaseId(p.getId()).isEmpty())
                .limit(appProperties.getFulfillment().getRepairJobBatchSize())
                .toList();
    }

    private void repairSingle(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE || purchase.getPaymentId() == null) {
            return;
        }
        boolean alreadyHas = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).isPresent();
        if (alreadyHas) {
            return;
        }
        log.info("Repairing orphaned fulfillment for purchaseId={}", purchase.getId());
        if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
            fulfillmentGrantService.grantAddonFulfillment(purchase);
        } else if (purchase.getPackageId() != null) {
            planPackageRepository.findByIdWithItems(purchase.getPackageId()).ifPresent(pkg ->
                    fulfillmentGrantService.grantPackageFulfillment(purchase, pkg));
        }
    }
}
