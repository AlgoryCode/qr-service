package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentLedger;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentQuotaStrategy;
import com.ael.algoryqrservice.service.fulfillment.FulfillmentQuotaStrategyFactory;
import com.ael.algoryqrservice.service.fulfillment.UsageReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entitlement gate used by the rest of the application. Reads are answered by the
 * {@link FulfillmentQuotaStrategy} selected for the user; writes go through the
 * {@link FulfillmentLedger}.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentGateService {

    private static final List<FulfillmentDetailSource> ADDON_ONLY =
            List.of(FulfillmentDetailSource.ADDON_PURCHASE);
    private static final List<FulfillmentDetailSource> PACKAGE_THEN_ADDON =
            List.of(FulfillmentDetailSource.PACKAGE_INCLUDE, FulfillmentDetailSource.ADDON_PURCHASE);
    private static final List<FulfillmentDetailSource> ADDON_THEN_PACKAGE =
            List.of(FulfillmentDetailSource.ADDON_PURCHASE, FulfillmentDetailSource.PACKAGE_INCLUDE);

    private final FulfillmentQuotaStrategyFactory strategyFactory;
    private final FulfillmentLedger ledger;

    @Transactional(readOnly = true)
    public boolean hasScope(Long userId, String scopeCode) {
        return strategyFor(userId).hasScope(userId, scopeCode);
    }

    @Transactional(readOnly = true)
    public int sumAddonQuantity(Long userId, String featureCode) {
        return strategyFor(userId).sumAddonQuantity(userId, featureCode);
    }

    @Transactional(readOnly = true)
    public int remainingQuantity(Long userId, String featureCode, boolean addonOnly) {
        return ledger.remainingQuantity(userId, featureCode, addonOnly);
    }

    @Transactional
    public FulfillmentConsumeResult consumeAddon(
            Long userId,
            String featureCode,
            int amount,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        return consume(userId, featureCode, amount, ADDON_ONLY, referenceType, referenceId);
    }

    @Transactional
    public FulfillmentConsumeResult consumeFeature(
            Long userId,
            String featureCode,
            int amount,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        return consume(userId, featureCode, amount, PACKAGE_THEN_ADDON, referenceType, referenceId);
    }

    @Transactional
    public void releaseAddon(
            Long userId,
            String featureCode,
            int amount,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        release(userId, featureCode, amount, ADDON_ONLY, referenceType, referenceId);
    }

    @Transactional
    public void releaseFeature(
            Long userId,
            String featureCode,
            int amount,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        release(userId, featureCode, amount, ADDON_THEN_PACKAGE, referenceType, referenceId);
    }

    @Transactional
    public void logFeatureUsage(
            Long userId,
            String featureCode,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        ledger.logUsage(userId, featureCode, UsageReference.of(referenceType, referenceId));
    }

    private FulfillmentConsumeResult consume(
            Long userId,
            String featureCode,
            int amount,
            List<FulfillmentDetailSource> sources,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        if (!isLedgerBacked(userId, amount)) {
            return new FulfillmentConsumeResult(0, null, null);
        }
        return ledger.consume(userId, featureCode, amount, sources, UsageReference.of(referenceType, referenceId));
    }

    private void release(
            Long userId,
            String featureCode,
            int amount,
            List<FulfillmentDetailSource> sources,
            FulfillmentReferenceType referenceType,
            Long referenceId
    ) {
        if (!isLedgerBacked(userId, amount)) {
            return;
        }
        ledger.release(userId, featureCode, amount, sources, UsageReference.of(referenceType, referenceId));
    }

    private boolean isLedgerBacked(Long userId, int amount) {
        return amount > 0 && strategyFor(userId).supportsLedger();
    }

    private FulfillmentQuotaStrategy strategyFor(Long userId) {
        return strategyFactory.strategyFor(userId);
    }
}
