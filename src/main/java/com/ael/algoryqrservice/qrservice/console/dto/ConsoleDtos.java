package com.ael.algoryqrservice.qrservice.console.dto;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceMaintenanceKind;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceTournamentStatus;
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
import java.time.LocalDate;
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
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal multiHourlyRate,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal purchaseCost,
            LocalDate purchasedOn
    ) {
    }

    public record GameRef(Long id, String name) {
    }

    /** {@code publicToken} goes in the device's QR link. */
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
            ReservationResponse nextReservation,
            String publicToken,
            boolean maintenance,
            String maintenanceNote,
            LocalDateTime maintenanceSince,
            BigDecimal purchaseCost,
            LocalDate purchasedOn,
            List<GameRef> games
    ) {
    }

    // ---- Game library ------------------------------------------------------------------------------------------

    public record GameRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String genre,
            @Min(1) @Max(64) Integer maxPlayers
    ) {
    }

    public record GameResponse(Long id, Long branchId, String name, String genre, Integer maxPlayers, List<Long> consoleIds) {
    }

    /** Replaces the games installed on a device. */
    public record ConsoleGamesRequest(@NotNull List<@NotNull Long> gameIds) {
    }

    // ---- Maintenance and costs ---------------------------------------------------------------------------------

    public record MaintenanceStartRequest(DeviceMaintenanceKind kind, @Size(max = 255) String note) {
    }

    public record MaintenanceEndRequest(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal cost,
            @Size(max = 255) String note
    ) {
    }

    /** A cost without downtime, e.g. a new controller. */
    public record ExpenseRequest(
            DeviceMaintenanceKind kind,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal cost,
            @Size(max = 255) String note
    ) {
    }

    public record MaintenanceLogResponse(
            Long id,
            Long consoleId,
            DeviceMaintenanceKind kind,
            String note,
            BigDecimal cost,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
    }

    // ---- Packages ----------------------------------------------------------------------------------------------

    /** {@code startTime}/{@code endTime} (both or neither) limit when the package can be started. */
    public record PackageRequest(
            @NotBlank @Size(max = 80) String name,
            DeviceType deviceType,
            @NotNull @Min(1) @Max(1440) Integer minutes,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(min = 1, max = 7) List<@Min(1) @Max(7) Integer> days,
            LocalTime startTime,
            LocalTime endTime,
            Boolean active
    ) {
    }

    public record PackageResponse(
            Long id,
            Long branchId,
            String name,
            DeviceType deviceType,
            int minutes,
            BigDecimal price,
            List<Integer> days,
            LocalTime startTime,
            LocalTime endTime,
            boolean active,
            boolean availableNow
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
            @Min(1) @Max(8) Integer controllerCount,
            Long packageId,
            Long gameId
    ) {

        public StartSessionRequest(String customerName, String note, Long customerId, Integer plannedMinutes, Integer controllerCount) {
            this(customerName, note, customerId, plannedMinutes, controllerCount, null, null);
        }
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
            Boolean useLoyaltyMinutes,
            Boolean useMembershipMinutes,
            Boolean useBonusBalance
    ) {

        public FinishSessionRequest(
                DevicePaymentMethod paymentMethod,
                BigDecimal discountAmount,
                BigDecimal discountPercent,
                Boolean useLoyaltyMinutes
        ) {
            this(paymentMethod, discountAmount, discountPercent, useLoyaltyMinutes, null, null);
        }
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
            List<SessionSegmentResponse> segments,
            Long packageId,
            String packageName,
            Integer packageMinutes,
            BigDecimal packagePrice,
            Long gameId,
            String gameName,
            Integer membershipMinutesUsed,
            BigDecimal tierDiscountAmount,
            BigDecimal bonusUsed,
            Integer extensionRequestMinutes,
            LocalDateTime extensionRequestedAt
    ) {
    }

    /**
     * What finishing now would charge with the given options; the finish dialog shows it. Deductions apply in order:
     * membership minutes, loyalty minutes, level discount (time only, not on packages), bonus balance, manual
     * discount. "Available" fields say what the customer has, whatever was asked.
     */
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
            BigDecimal loyaltyValue,
            String packageName,
            int membershipMinutesAvailable,
            int membershipMinutesUsed,
            BigDecimal membershipValue,
            String tierName,
            BigDecimal tierDiscountPercent,
            BigDecimal tierDiscountAmount,
            BigDecimal bonusBalance,
            BigDecimal bonusUsed,
            BigDecimal manualDiscount,
            int loyaltyMinutesUsed,
            BigDecimal loyaltyDiscount,
            BigDecimal totalDiscount,
            BigDecimal amount
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

    /** {@code referralCode}: a friend's code, only read when the card is opened. */
    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String note,
            @Size(max = 16) String referralCode
    ) {

        public CustomerRequest(String name, String phone, String note) {
            this(name, phone, note, null);
        }
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
            BigDecimal totalSpent,
            BigDecimal bonusBalance,
            String referralCode,
            Long referredById,
            int visits,
            LocalDateTime lastVisitAt,
            String tierName,
            BigDecimal tierDiscountPercent,
            int membershipMinutes
    ) {
    }

    /** Gift money on the bonus balance; it is spent as a discount and never booked as income. */
    public record GiftRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 255) String note
    ) {
    }

    public record CustomerInsightsResponse(int lapsedDays, List<CustomerResponse> top, List<CustomerResponse> lapsed) {
    }

    // ---- Programme: levels, referral, memberships --------------------------------------------------------------

    public record TierRequest(
            @NotBlank @Size(max = 40) String name,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minSpent,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent
    ) {
    }

    public record TierResponse(Long id, String name, BigDecimal minSpent, BigDecimal discountPercent) {
    }

    /** Replaces the levels. A null referral amount turns that reward off. */
    public record ProgramRequest(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal referralRewardAmount,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal referralWelcomeAmount,
            @NotNull @Size(max = 10) @Valid List<TierRequest> tiers
    ) {
    }

    public record ProgramResponse(
            BigDecimal referralRewardAmount,
            BigDecimal referralWelcomeAmount,
            List<TierResponse> tiers
    ) {
    }

    public record MembershipPlanRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(1) @Max(100000) Integer minutes,
            @NotNull @Min(1) @Max(366) Integer periodDays,
            DeviceType deviceType,
            Boolean active
    ) {
    }

    public record MembershipPlanResponse(
            Long id,
            String name,
            BigDecimal price,
            int minutes,
            int periodDays,
            DeviceType deviceType,
            boolean active
    ) {
    }

    /** Payment defaults to CASH; BALANCE takes it from the customer's paid balance. */
    public record SellMembershipRequest(@NotNull Long planId, DevicePaymentMethod paymentMethod, Long branchId) {
    }

    public record MembershipResponse(
            Long id,
            Long customerId,
            Long planId,
            String planName,
            DeviceType deviceType,
            int minutesTotal,
            int minutesLeft,
            BigDecimal price,
            DevicePaymentMethod paymentMethod,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean cancelled,
            boolean usable
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
            @Size(max = 255) String note,
            Long branchId
    ) {

        public TopUpRequest(BigDecimal amount, DevicePaymentMethod paymentMethod, String note) {
            this(amount, paymentMethod, note, null);
        }
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
            List<PaymentReportRow> payments,
            List<GameReportRow> games
    ) {
    }

    public record GameReportRow(Long gameId, String gameName, int sessions, int playedMinutes) {
    }

    /**
     * Lifetime figures per device. {@code paybackPercent} is (revenue − costs) over the purchase cost;
     * {@code estimatedPaybackDays} projects the last 30 days' revenue and is null when it cannot be told.
     */
    public record DeviceProfitRow(
            Long consoleId,
            String consoleName,
            DeviceType deviceType,
            BigDecimal purchaseCost,
            LocalDate purchasedOn,
            int playedMinutes,
            BigDecimal revenue,
            BigDecimal maintenanceCost,
            BigDecimal net,
            BigDecimal paybackPercent,
            boolean paidBack,
            BigDecimal last30Revenue,
            Integer estimatedPaybackDays
    ) {
    }

    public record ProfitabilityResponse(
            Long branchId,
            BigDecimal purchaseCost,
            BigDecimal revenue,
            BigDecimal maintenanceCost,
            BigDecimal net,
            List<DeviceProfitRow> devices
    ) {
    }

    // ---- Shifts ------------------------------------------------------------------------------------------------

    public record OpenShiftRequest(@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal openingCash) {
    }

    public record CloseShiftRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal countedCash,
            @Size(max = 500) String note
    ) {
    }

    public record PaymentTotal(DevicePaymentMethod paymentMethod, int count, BigDecimal amount) {
    }

    /**
     * Money the person took during the shift: sessions they closed, balance top-ups, membership sales and tournament
     * fees. {@code expectedCash} = opening cash + cash taken; for an open shift the figures run up to now.
     */
    public record ShiftResponse(
            Long id,
            Long branchId,
            Long staffId,
            String staffName,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            BigDecimal openingCash,
            int sessions,
            BigDecimal sessionsTotal,
            List<PaymentTotal> sessionPayments,
            List<PaymentTotal> topUps,
            List<PaymentTotal> memberships,
            List<PaymentTotal> tournamentFees,
            BigDecimal cashIn,
            BigDecimal expectedCash,
            BigDecimal countedCash,
            BigDecimal difference,
            String note
    ) {
    }

    public record ShiftPageResponse(
            List<ShiftResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    // ---- Tournaments -------------------------------------------------------------------------------------------

    public record TournamentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String gameName,
            @NotNull LocalDateTime startsAt,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal entryFee,
            @Size(max = 255) String prizeNote,
            @NotNull @Min(2) @Max(128) Integer maxPlayers
    ) {
    }

    /** {@code paymentMethod} set means the fee was taken at sign-up. */
    public record TournamentPlayerRequest(
            Long customerId,
            @Size(max = 120) String name,
            @Size(max = 32) String phone,
            DevicePaymentMethod paymentMethod
    ) {
    }

    public record TournamentPayRequest(DevicePaymentMethod paymentMethod) {
    }

    public record MatchResultRequest(@NotNull Long winnerPlayerId, @Size(max = 40) String score) {
    }

    public record TournamentPlayerResponse(
            Long id,
            Long customerId,
            String name,
            String phone,
            boolean paid,
            DevicePaymentMethod paymentMethod,
            LocalDateTime paidAt
    ) {
    }

    /** {@code ready}: both players are known and no winner yet. */
    public record TournamentMatchResponse(
            Long id,
            int round,
            int position,
            Long player1Id,
            String player1Name,
            Long player2Id,
            String player2Name,
            Long winnerId,
            String score,
            boolean ready
    ) {
    }

    public record TournamentResponse(
            Long id,
            Long branchId,
            String name,
            String gameName,
            LocalDateTime startsAt,
            BigDecimal entryFee,
            String prizeNote,
            int maxPlayers,
            DeviceTournamentStatus status,
            Long winnerPlayerId,
            String winnerName,
            int playerCount,
            int paidCount,
            BigDecimal collected,
            int rounds,
            List<TournamentPlayerResponse> players,
            List<TournamentMatchResponse> matches
    ) {
    }

    // ---- Public (device QR page) -------------------------------------------------------------------------------

    public record PublicItem(String name, int quantity, BigDecimal lineTotal) {
    }

    /** No customer details: anyone at the device can scan its QR. */
    public record PublicSessionResponse(
            LocalDateTime startedAt,
            long playedSeconds,
            Integer plannedMinutes,
            Long remainingSeconds,
            boolean paused,
            String packageName,
            String gameName,
            BigDecimal hourlyRate,
            BigDecimal timeAmount,
            BigDecimal itemsAmount,
            BigDecimal amount,
            List<PublicItem> items,
            Integer extensionRequestMinutes,
            LocalDateTime extensionRequestedAt
    ) {
    }

    /** {@code status}: FREE, BUSY, PAUSED or MAINTENANCE. */
    public record PublicDeviceResponse(
            String deviceName,
            DeviceType deviceType,
            String branchName,
            String status,
            BigDecimal hourlyRate,
            List<String> games,
            PublicSessionResponse session,
            LocalDateTime serverTime
    ) {
    }

    public record PublicExtensionRequest(@NotNull @Min(5) @Max(240) Integer minutes) {
    }
}
