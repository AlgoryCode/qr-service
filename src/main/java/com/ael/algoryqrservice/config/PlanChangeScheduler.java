package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.PlanChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanChangeScheduler {

    private final PlanChangeService planChangeService;

    @Scheduled(fixedRate = 300_000)
    public void executeDuePlanChanges() {
        try {
            planChangeService.executeDueScheduled();
        } catch (RuntimeException exception) {
            log.error("Plan change scheduler failed", exception);
        }
    }
}
