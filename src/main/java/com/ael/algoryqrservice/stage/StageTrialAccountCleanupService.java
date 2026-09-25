package com.ael.algoryqrservice.stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageTrialAccountCleanupService {

    private final StageTrialFixtureProperties properties;
    private final StageTrialAssignmentRepository assignmentRepository;
    private final AuthMerchantSoftDeleteClient authMerchantSoftDeleteClient;
    private final StageTrialAccountCleanupTx cleanupTx;

    public void purgeDue() {
        if (!properties.isReady()) {
            return;
        }
        List<Long> userIds = assignmentRepository.findUserIdsExcept(properties.getTemplateUserId());
        for (Long userId : userIds) {
            purgeOne(userId);
        }
    }

    private void purgeOne(Long userId) {
        try {
            if (!cleanupTx.isDue(userId)) {
                return;
            }
            authMerchantSoftDeleteClient.softDelete(userId);
            cleanupTx.markDeleted(userId);
        } catch (RuntimeException exception) {
            log.error("Stage trial account cleanup failed. userId={}", userId, exception);
        }
    }
}
