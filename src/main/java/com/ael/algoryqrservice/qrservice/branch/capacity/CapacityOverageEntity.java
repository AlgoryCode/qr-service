package com.ael.algoryqrservice.qrservice.branch.capacity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only mapping of {@code capacity_overages} (written by {@link CapacityOverageService} in SQL).
 * Mapped so the schema exists wherever Hibernate creates it, e.g. in tests.
 */
@Entity
@Table(name = "capacity_overages")
@Getter
@NoArgsConstructor
public class CapacityOverageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    @Column(name = "used_value", nullable = false)
    private long usedValue;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(length = 16)
    private String resolution;
}
