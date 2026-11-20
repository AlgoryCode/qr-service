package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One rental. Open while {@code endedAt} is null. The play time lives in {@link DeviceSessionSegment}s (pausing
 * and switching device split it); {@code consoleId} and {@code hourlyRate} are the current device and rate.
 * {@code amount} is what was charged: time + items - discount.
 */
@Entity
@Table(name = "tbl_console_session")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConsoleSession extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "console_id", nullable = false)
    private Long consoleId;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(length = 500)
    private String note;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "controller_count", nullable = false)
    @lombok.Builder.Default
    private int controllerCount = 2;

    /** Time the customer asked for; the screen counts down and warns when it runs out. Null = open-ended. */
    @Column(name = "planned_minutes")
    private Integer plannedMinutes;

    /** Set while the session is paused; paused time is not charged. */
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "time_amount", precision = 12, scale = 2)
    private BigDecimal timeAmount;

    @Column(name = "items_amount", precision = 12, scale = 2)
    private BigDecimal itemsAmount;

    @Column(name = "gross_amount", precision = 12, scale = 2)
    private BigDecimal grossAmount;

    /** Manual discount plus the value of the loyalty minutes used. */
    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "loyalty_minutes_used")
    private Integer loyaltyMinutesUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private DevicePaymentMethod paymentMethod;

    @Column(name = "opened_by_staff_id")
    private Long openedByStaffId;

    @Column(name = "closed_by_staff_id")
    private Long closedByStaffId;

    public boolean isPaused() {
        return endedAt == null && pausedAt != null;
    }

    public boolean isActive() {
        return endedAt == null;
    }
}
