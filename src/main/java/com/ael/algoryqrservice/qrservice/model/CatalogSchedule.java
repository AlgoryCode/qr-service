package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A catalog change that happens on a date: a new product unit price, or a package discount
 * campaign. With {@code endsAt} the previous values come back when the window closes.
 */
@Entity
@Table(name = "catalog_schedule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSchedule {

    public enum TargetType { PRODUCT, PACKAGE }

    public enum Status { SCHEDULED, ACTIVE, DONE, CANCELLED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "monthly_discount", precision = 12, scale = 2)
    private BigDecimal monthlyDiscount;

    @Column(name = "yearly_discount", precision = 12, scale = 2)
    private BigDecimal yearlyDiscount;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "prev_unit_price", precision = 12, scale = 2)
    private BigDecimal prevUnitPrice;

    @Column(name = "prev_monthly_discount", precision = 12, scale = 2)
    private BigDecimal prevMonthlyDiscount;

    @Column(name = "prev_yearly_discount", precision = 12, scale = 2)
    private BigDecimal prevYearlyDiscount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** The window this change occupies; a permanent change occupies only its start instant. */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        LocalDateTime myEnd = endsAt == null ? startsAt : endsAt;
        LocalDateTime otherEnd = end == null ? start : end;
        return !start.isAfter(myEnd) && !startsAt.isAfter(otherEnd);
    }
}
