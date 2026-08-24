package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.BranchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class BranchBackfillRunner implements ApplicationRunner {

    private final BranchService branchService;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeed().isBranchBackfill()) {
            return;
        }
        try {
            int created = branchService.backfillMissingBranches();
            log.info("Branch backfill completed, created={}", created);
        } catch (Exception exception) {
            log.warn("Branch backfill failed: {}", exception.getMessage());
        }
    }
}
