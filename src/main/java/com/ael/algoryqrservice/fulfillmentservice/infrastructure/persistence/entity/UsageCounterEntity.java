package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Read-only mapping of {@code usage_counters} (written by {@code MeteringService} in SQL). Mapped so
 * the schema exists wherever Hibernate creates it, e.g. in tests.
 */
@Entity
@Table(name = "usage_counters")
@IdClass(UsageCounterEntity.Key.class)
@Getter
@NoArgsConstructor
public class UsageCounterEntity {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Id
    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Id
    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(nullable = false)
    private long used;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static class Key implements Serializable {
        private Long merchantId;
        private String featureKey;
        private String periodKey;

        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(merchantId, key.merchantId)
                    && Objects.equals(featureKey, key.featureKey) && Objects.equals(periodKey, key.periodKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(merchantId, featureKey, periodKey);
        }
    }
}
