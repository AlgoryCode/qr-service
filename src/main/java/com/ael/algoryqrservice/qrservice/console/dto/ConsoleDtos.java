package com.ael.algoryqrservice.qrservice.console.dto;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceReservationStatus;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Oyun & Cihaz Takip API. Paths and class names still say "console" for compatibility. */
public final class ConsoleDtos {

    private ConsoleDtos() {
    }

    // ---- Devices and rates -------------------------------------------------------------------------------------

    /** Null rates fall back to the type rate, then the branch default. A null type is PLAYSTATION. */
    public record ConsoleRequest(
            @NotBlank @Size(max = 120) String name,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
            DeviceType deviceType,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal multiHourlyRate
    ) {
    }

    public record ConsoleResponse(
            Long id,
            Long branchId,
            String name,
            DeviceType deviceType,
            BigDecimal hourlyRate,
            BigDecimal multiHourlyRate,
            BigDecimal effectiveHourlyRate,
            BigDecimal effectiveMultiHourlyRate,
            boolean busy,
            ReservationResponse nextReservation
    ) {
    }

    public record RateSettingRequest(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal defaultHourlyRate,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal defaultMultiHourlyRate,
            @Min(0) @Max(1440) Integer minimumMinutes,
            @Min(1) @Max(120) Integer roundingMinutes,
            @Min(1) @Max(100000) Integer loyaltyEveryMinutes,
            @Min(1) @Max(10000) Integer loyaltyRewardMinutes
    ) {
    }

    public record RateSettingResponse(
            Long branchId,
            BigDecimal defaultHourlyRate,
            BigDecimal defaultMultiHourlyRate,
            int minimumMinutes,
            int roundingMinutes,
            Integer loyaltyEveryMinutes,
            Integer loyaltyRewardMinutes
    ) {
    }

    public record TypeRate(
            @NotNull DeviceType deviceType,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal multiHourlyRate
    ) {
    }

    /** Replaces the branch's type rates; a type left out (or with both rates null) has none. */
    public record TypeRatesRequest(@NotNull @Valid List<TypeRate> rates) {
    }

    /** {@code days} uses ISO numbers, 1 = Monday ... 7 = Sunday. A null {@code deviceType} covers every type. */
    public record TariffRequest(
            @NotBlank @Size(max = 80) String name,
            DeviceType deviceType,
            @NotNull @Size(min = 1, max = 7) List<@Min(1) @Max(7) Integer> days,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @DecimalMin("-100") @DecimalMax("500") @Digits(integer = 3, fraction = 2) BigDecimal adjustPercent
    ) {
    }

    public record TariffResponse(
            Long id,
            Long branchId,
            String name,
            DeviceType deviceType,
            List<Integer> days,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal adjustPercent
    ) {
    }

    // ---- Sessions ----------------------------------------------------------------------------------------------

    /**
     * {@code customerId} links a customer card (its name is used when {@code customerName} is blank).
     * {@code plannedMinutes} starts a countdown. {@code controllerCount} 3 or more uses the 4-controller rate.
     */
    public record StartSessionRequest(
            @Size(max = 120) String customerName,
            @Size(max = 500) String note,
            Long customerId,
            @Min(1) @Max(1440) Integer plannedMinutes,
            @Min(1) @Max(8) Integer controllerCount
    ) {
    }

    /** Moves the session to another device and/or changes the controller count; the time is split there. */
    public record TransferRequest(
            Long consoleId,
            @Min(1) @Max(8) Integer controllerCount
    ) {
    }

    /** Adds to the planned time; a session without a plan gets one of {@code minutes} from the start. */
    public record ExtendRequest(@NotNull @Min(1) @Max(1440) Integer minutes) {
    }

    /** Either {@code menuProductId}, or a free item with {@code name} and {@code unitPrice}. */
    public record AddItemRequest(
            Long menuProductId,
            @Size(max = 200) String name,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @Min(1) @Max(999) Integer quantity
    ) {
    }

    /**
     * Payment defaults to CASH. Give at most one of {@code discountAmount} / {@code discountPercent}; they apply after
     * the loyalty minutes. BALANCE needs a customer with enough balance.
     */
    public record FinishSessionRequest(
            DevicePaymentMethod paymentMethod,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            Boolean useLoyaltyMinutes
    ) {
    }

    public record SessionItemResponse(
            Long id,
            Long menuProductId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {
    }

    public record SessionSegmentResponse(
            Long consoleId,
            String consoleName,
            DeviceType deviceType,
            BigDecimal hourlyRate,
            int controllerCount,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
    }

    /**
     * For an open session the amounts and minutes are running totals up to now; once it is finished they are the
     * stored, final values. {@code playedSeconds} excludes paused time; {@code durationMinutes} is what is charged
     * (after rounding and the minimum).
     */
    public record SessionResponse(
            Long id,
            Long branchId,
            Long consoleId,
            String consoleName,
            DeviceType deviceType,
            Long customerId,
            String customerName,
            String note,
            BigDecimal hourlyRate,
            int controllerCount,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime pausedAt,
            boolean paused,
            Integer plannedMinutes,
            long playedSeconds,
            int durationMinutes,
            BigDecimal timeAmount,
            BigDecimal itemsAmount,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            Integer loyaltyMinutesUsed,
            BigDecimal amount,
            DevicePaymentMethod paymentMethod,
            boolean active,
            Long reservationId,
            List<SessionItemResponse> items,
            List<SessionSegmentResponse> segments
    ) {
    }

    /** What finishing now would charge, before discount; for the finish dialog. */
    public record SessionQuoteResponse(
            Long sessionId,
            long playedSeconds,
            int durationMinutes,
            BigDecimal timeAmount,
            BigDecimal itemsAmount,
            BigDecimal grossAmount,
            Long customerId,
            String customerName,
            BigDecimal customerBalance,
            int loyaltyMinutesAvailable,
            int loyaltyMinutesUsable,
            BigDecimal loyaltyValue
    ) {
    }

    public record SessionPageResponse(
            List<SessionResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record ProductOption(
            Long menuProductId,
            Long menuId,
            String name,
            BigDecimal price,
            String imageUrl
    ) {
    }

    // ---- Customers ---------------------------------------------------------------------------------------------

    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String note
    ) {
    }

    public record CustomerResponse(
            Long id,
            String name,
            String phone,
            String note,
            BigDecimal balance,
            int freeMinutes,
            int loyaltyProgressMinutes,
            int totalMinutes,
            BigDecimal totalSpent
    ) {
    }

    public record CustomerPageResponse(
            List<CustomerResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    /** Loads prepaid balance; {@code amount} is what the customer paid, by {@code paymentMethod} (not BALANCE). */
    public record TopUpRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            DevicePaymentMethod paymentMethod,
            @Size(max = 255) String note
    ) {
    }

    /** Adds (positive) or removes (negative) balance and/or free minutes by hand. */
    public record AdjustRequest(
            @Digits(integer = 10, fraction = 2) BigDecimal balanceDelta,
            @Min(-100000) @Max(100000) Integer freeMinutesDelta,
            @Size(max = 255) String note
    ) {
    }

    public record CustomerTxnResponse(
            Long id,
            DeviceCustomerTxnType type,
            BigDecimal amount,
            DevicePaymentMethod paymentMethod,
            Long sessionId,
            String note,
            LocalDateTime createdAt
    ) {
    }

    public record CustomerTxnPageResponse(
            List<CustomerTxnResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    // ---- Reservations ------------------------------------------------------------------------------------------

    public record ReservationRequest(
            @NotNull Long consoleId,
            Long customerId,
            @Size(max = 120) String customerName,
            @Size(max = 32) String phone,
            @Size(max = 500) String note,
            @NotNull LocalDateTime startsAt,
            @NotNull @Min(5) @Max(1440) Integer durationMinutes
    ) {
    }

    public record ReservationResponse(
            Long id,
            Long branchId,
            Long consoleId,
            String consoleName,
            Long customerId,
            String customerName,
            String phone,
            String note,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            DeviceReservationStatus status,
            Long sessionId
    ) {
    }

    /** Starts the reserved session; the reserved length becomes the planned time. */
    public record StartReservationRequest(
            @Min(1) @Max(8) Integer controllerCount
    ) {
    }

    // ---- Report ------------------------------------------------------------------------------------------------

    public record DeviceReportRow(
            Long consoleId,
            String consoleName,
            DeviceType deviceType,
            int sessions,
            int playedMinutes,
            BigDecimal revenue,
            BigDecimal occupancyPercent
    ) {
    }

    public record HourReportRow(int hour, int playedMinutes, int sessionsStarted, BigDecimal revenue) {
    }

    public record PaymentReportRow(DevicePaymentMethod paymentMethod, int sessions, BigDecimal amount) {
    }

    /** Sessions that finished between {@code from} and {@code to}. Occupancy is played time over the period. */
    public record ReportResponse(
            Long branchId,
            LocalDateTime from,
            LocalDateTime to,
            int sessions,
            int playedMinutes,
            BigDecimal timeRevenue,
            BigDecimal itemsRevenue,
            BigDecimal discounts,
            BigDecimal revenue,
            BigDecimal averageSessionMinutes,
            BigDecimal averageSessionAmount,
            BigDecimal occupancyPercent,
            List<DeviceReportRow> devices,
            List<HourReportRow> hours,
            List<PaymentReportRow> payments
    ) {
    }
}
