package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Read-only mapping of the entitlement snapshot (written by {@code EntitlementSnapshotService} in SQL).
 * Mapped so the schema exists wherever Hibernate creates it, e.g. in tests.
 */
@Entity
@Table(name = "merchant_entitlements")
@IdClass(MerchantEntitlementEntity.Key.class)
@Getter
@NoArgsConstructor
public class MerchantEntitlementEntity {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Id
    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    @Column(nullable = false)
    private boolean unlimited;

    @Column(nullable = false)
    private int grants;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static class Key implements Serializable {
        private Long merchantId;
        private String featureKey;

        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(merchantId, key.merchantId)
                    && Objects.equals(featureKey, key.featureKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(merchantId, featureKey);
        }
    }
}
