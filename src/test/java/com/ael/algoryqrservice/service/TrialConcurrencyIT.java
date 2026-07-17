package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TrialConcurrencyIT {
    @Autowired PurchaseRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void trialUniqueConstraint_whenConcurrentInserts_thenOnlyOneSucceeds() throws Exception {
        long userId = 987654321L;
        jdbcTemplate.update("DELETE FROM tbl_purchase WHERE user_id = ?", userId);
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS test_uk_trial_user_type "
                + "ON tbl_purchase(user_id, purchase_type)");
        Callable<Boolean> insert = () -> {
            try {
                return transactionTemplate.execute(status -> {
                    repository.saveAndFlush(trial(userId));
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
            jdbcTemplate.update("DELETE FROM tbl_purchase WHERE user_id = ?", userId);
            jdbcTemplate.execute("DROP INDEX IF EXISTS test_uk_trial_user_type");
        }
    }

    private Purchase trial(long userId) {
        LocalDateTime now = LocalDateTime.now();
        return Purchase.builder().userId(userId).packageId(1L).packageCode(CatalogPackages.PRO_PACKAGE)
                .packageName("PRO").price(BigDecimal.ZERO).currency("TRY").purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE).startsAt(now).expiresAt(now.plusDays(30)).build();
    }
}
