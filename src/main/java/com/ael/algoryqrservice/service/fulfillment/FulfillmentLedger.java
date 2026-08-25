package com.ael.algoryqrservice.service.fulfillment;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Owns every write against {@code tbl_fulfillment_detail}. Quota rows are locked with
 * {@code PESSIMISTIC_WRITE}, so all entry points here must run in a writable transaction.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentLedger {

    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final FulfillmentUsageLogRepository usageLogRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final ConsumeAdjustment consumeAdjustment;
    private final ReleaseAdjustment releaseAdjustment;

    @Transactional
    public FulfillmentConsumeResult consume(
            Long userId,
            String featureCode,
            int amount,
            List<FulfillmentDetailSource> sources,
            UsageReference reference
    ) {
        return adjust(userId, featureCode, amount, sources, consumeAdjustment, reference);
    }

    @Transactional
    public void release(
            Long userId,
            String featureCode,
            int amount,
            List<FulfillmentDetailSource> sources,
            UsageReference reference
    ) {
        adjust(userId, featureCode, amount, sources, releaseAdjustment, reference);
    }

    /**
     * Rewrites {@code usedQuantity} so it mirrors the real world (e.g. the number of live menus),
     * spilling over from the first source to the next once a row is full.
     */
    @Transactional
    public void replaceUsedQuantity(
            Long userId,
            String featureCode,
            int usedTotal,
            List<FulfillmentDetailSource> sources
    ) {
        LocalDateTime now = AppTime.nowLocal();
        int unassigned = Math.max(0, usedTotal);
        for (FulfillmentDetailSource source : sources) {
            for (FulfillmentDetail detail : lockActiveDetails(userId, featureCode, source, now)) {
                if (detail.isUnlimited()) {
                    continue;
                }
                int capacity = detail.getQuantity() == null ? 0 : detail.getQuantity();
                int used = Math.min(capacity, unassigned);
                detail.setUsedQuantity(used);
                fulfillmentDetailRepository.save(detail);
                unassigned -= used;
            }
        }
    }

    @Transactional
    public void logUsage(Long userId, String featureCode, UsageReference reference) {
        LocalDateTime now = AppTime.nowLocal();
        fulfillmentDetailRepository.findAllActiveByUserId(userId, now).stream()
                .filter(detail -> matchesFeatureOrScope(detail, featureCode))
                .findFirst()
                .ifPresent(detail -> writeUsageLog(detail, userId, FulfillmentUsageAction.CONSUME, 1, reference));
    }

    @Transactional(readOnly = true)
    public List<FulfillmentDetail> listActiveDetails(Long userId) {
        return fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
    }

    @Transactional(readOnly = true)
    public int remainingQuantity(Long userId, String featureCode, boolean addonOnly) {
        int remaining = 0;
        for (FulfillmentDetail detail : listActiveDetails(userId)) {
            if (!Objects.equals(detail.getFeatureCode(), featureCode)) {
                continue;
            }
            if (addonOnly && detail.getSource() != FulfillmentDetailSource.ADDON_PURCHASE) {
                continue;
            }
            if (detail.isUnlimited()) {
                return Integer.MAX_VALUE;
            }
            remaining += detail.remainingQuantity();
        }
        return remaining;
    }

    private FulfillmentConsumeResult adjust(
            Long userId,
            String featureCode,
            int amount,
            List<FulfillmentDetailSource> sources,
            FulfillmentDetailAdjustment adjustment,
            UsageReference reference
    ) {
        LocalDateTime now = AppTime.nowLocal();
        int remaining = amount;
        FulfillmentDetail lastTouched = null;

        for (FulfillmentDetailSource source : sources) {
            if (remaining <= 0) {
                break;
            }
            for (FulfillmentDetail detail : lockActiveDetails(userId, featureCode, source, now)) {
                if (remaining <= 0) {
                    break;
                }
                if (detail.isUnlimited()) {
                    writeUsageLog(detail, userId, adjustment.action(), remaining, reference);
                    return new FulfillmentConsumeResult(amount, purchaseIdOf(detail), detail.getId());
                }
                int applied = Math.min(adjustment.capacity(detail), remaining);
                if (applied <= 0) {
                    continue;
                }
                adjustment.apply(detail, applied);
                fulfillmentDetailRepository.save(detail);
                writeUsageLog(detail, userId, adjustment.action(), applied, reference);
                remaining -= applied;
                lastTouched = detail;
            }
        }

        if (lastTouched == null) {
            return new FulfillmentConsumeResult(amount - remaining, null, null);
        }
        return new FulfillmentConsumeResult(amount - remaining, purchaseIdOf(lastTouched), lastTouched.getId());
    }

    private List<FulfillmentDetail> lockActiveDetails(
            Long userId,
            String featureCode,
            FulfillmentDetailSource source,
            LocalDateTime now
    ) {
        return fulfillmentDetailRepository.findAndLockActiveByFeatureCodeAndSource(userId, featureCode, source, now);
    }

    private boolean matchesFeatureOrScope(FulfillmentDetail detail, String featureCode) {
        return Objects.equals(detail.getFeatureCode(), featureCode)
                || Objects.equals(detail.getScopeCode(), featureCode);
    }

    private Long purchaseIdOf(FulfillmentDetail detail) {
        return grantFulfillmentRepository.findById(detail.getFulfillmentId())
                .map(GrantFulfillment::getPurchaseId)
                .orElse(null);
    }

    private void writeUsageLog(
            FulfillmentDetail detail,
            Long userId,
            FulfillmentUsageAction action,
            int amount,
            UsageReference reference
    ) {
        usageLogRepository.save(FulfillmentUsageLog.builder()
                .detailId(detail.getId())
                .userId(userId)
                .action(action)
                .amount(amount)
                .referenceType(reference == null ? null : reference.type())
                .referenceId(reference == null ? null : reference.id())
                .build());
    }
}
