package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.MenuPublicAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MenuPublicAccessBackfillRunner implements ApplicationRunner {

    private final MenuPublicAccessService menuPublicAccessService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            menuPublicAccessService.syncAllMenuOwners();
            log.info("Menu public access backfill completed");
        } catch (Exception exception) {
            log.warn("Menu public access backfill failed: {}", exception.getMessage());
        }
    }
}
