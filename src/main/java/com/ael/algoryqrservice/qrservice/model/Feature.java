package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Something a package or add-on grants, and how it is measured. */
@Entity
@Table(name = "features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feature {

    public enum Kind {
        /** On or off. */
        BOOLEAN,
        /** How many may exist at once; usage is counted live from the resources. */
        LIMIT,
        /** Uses per period, reset every {@link ResetPeriod}. */
        METERED,
        /** Prepaid units that are spent and not renewed. */
        CREDIT
    }

    public enum ResetPeriod { NONE, DAY, WEEK, MONTH }

    @Id
    @Column(name = "feature_key", length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(length = 32)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_period", nullable = false, length = 16)
    @Builder.Default
    private ResetPeriod resetPeriod = ResetPeriod.NONE;

    @Column(name = "parent_key", length = 64)
    private String parentKey;

    @Column(length = 500)
    private String description;

    /** Allowance per period for merchants with no grant of this feature; null means none. */
    @Column(name = "free_quota")
    private Integer freeQuota;

    /** Owning the parent feature makes this allowance unlimited unless a grant sets a number. */
    @Builder.Default
    @Column(name = "unlimited_with_parent", nullable = false)
    private boolean unlimitedWithParent = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
