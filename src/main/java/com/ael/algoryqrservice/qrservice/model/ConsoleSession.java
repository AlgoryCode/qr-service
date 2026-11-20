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

    /** Everything taken off the gross: membership and loyalty minutes, level discount, bonus balance, manual discount. */
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

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_name", length = 80)
    private String packageName;

    /** Minutes covered by {@link #packagePrice}; time beyond is charged at the normal rate. */
    @Column(name = "package_minutes")
    private Integer packageMinutes;

    @Column(name = "package_price", precision = 12, scale = 2)
    private BigDecimal packagePrice;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "game_name", length = 120)
    private String gameName;

    @Column(name = "membership_id")
    private Long membershipId;

    @Column(name = "membership_minutes_used")
    private Integer membershipMinutesUsed;

    @Column(name = "tier_discount_amount", precision = 12, scale = 2)
    private BigDecimal tierDiscountAmount;

    /** Gift/referral balance spent on this session (part of {@link #discountAmount}). */
    @Column(name = "bonus_used", precision = 12, scale = 2)
    private BigDecimal bonusUsed;

    /** More time the customer asked for from the device's QR page, waiting for staff. */
    @Column(name = "extension_request_minutes")
    private Integer extensionRequestMinutes;

    @Column(name = "extension_requested_at")
    private LocalDateTime extensionRequestedAt;

    public boolean isPaused() {
        return endedAt == null && pausedAt != null;
    }

    public boolean isActive() {
        return endedAt == null;
    }
}
