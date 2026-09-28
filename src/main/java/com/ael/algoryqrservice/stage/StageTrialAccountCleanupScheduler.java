package com.ael.algoryqrservice.stage;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StageTrialAccountCleanupScheduler {

    private final StageTrialAccountCleanupService stageTrialAccountCleanupService;

    @Scheduled(fixedRate = 300_000)
    public void purgeDueAccounts() {
        stageTrialAccountCleanupService.purgeDue();
    }
}
