package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.util.WritableTransactionGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry over every {@link FeatureUsageSynchronizer} bean, keyed by feature code.
 *
 * <p>Every synchronizer rewrites quota rows behind a pessimistic lock, so the whole pass is
 * skipped when it is reached from a read-only transaction.
 */
@Component
public class FeatureUsageSyncRegistry {

    private static final String OPERATION = "feature usage sync";

    private final Map<String, FeatureUsageSynchronizer> synchronizersByFeature;
    private final WritableTransactionGuard writableTransactionGuard;

    public FeatureUsageSyncRegistry(
            List<FeatureUsageSynchronizer> synchronizers,
            WritableTransactionGuard writableTransactionGuard
    ) {
        this.synchronizersByFeature = synchronizers.stream().collect(Collectors.toUnmodifiableMap(
                FeatureUsageSynchronizer::featureCode,
                Function.identity()
        ));
        this.writableTransactionGuard = writableTransactionGuard;
    }

    @Transactional
    public void synchronize(Long userId, String featureCode) {
        if (featureCode == null || !allowsWrites(userId)) {
            return;
        }
        FeatureUsageSynchronizer synchronizer = synchronizersByFeature.get(featureCode);
        if (synchronizer != null) {
            synchronizer.synchronize(userId);
        }
    }

    @Transactional
    public void synchronizeAll(Long userId) {
        if (!allowsWrites(userId)) {
            return;
        }
        synchronizersByFeature.values().forEach(synchronizer -> synchronizer.synchronize(userId));
    }

    private boolean allowsWrites(Long userId) {
        return userId != null && writableTransactionGuard.allowsWrites(OPERATION, userId);
    }
}
