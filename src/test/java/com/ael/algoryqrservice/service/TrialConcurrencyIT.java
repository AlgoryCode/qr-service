package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "QR_SERVICE_DB_IT", matches = "true")
class TrialConcurrencyIT {
    @Autowired TrialLogRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void trialLogUniqueConstraint_whenConcurrentInserts_thenOnlyOneSucceeds() throws Exception {
        long userId = 987654321L;
        jdbcTemplate.update("DELETE FROM tbl_trial_log WHERE user_id = ?", userId);
        Callable<Boolean> insert = () -> {
            try {
                return transactionTemplate.execute(status -> {
                    repository.saveAndFlush(log(userId));
                    return true;
                });
            } catch (RuntimeException exception) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Boolean> results = executor.invokeAll(List.of(insert, insert)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            return false;
                        }
                    })
                    .toList();
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            jdbcTemplate.update("DELETE FROM tbl_trial_log WHERE user_id = ?", userId);
        }
    }

    private TrialLog log(long userId) {
        LocalDateTime now = LocalDateTime.now();
        return TrialLog.builder()
                .userId(userId)
                .packageId(1L)
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .startedAt(now)
                .endsAt(now.plusDays(15))
                .durationDays(15)
                .status(TrialLogStatus.ACTIVE)
                .build();
    }
}
