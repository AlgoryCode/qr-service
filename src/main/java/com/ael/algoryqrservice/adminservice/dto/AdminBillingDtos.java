package com.ael.algoryqrservice.adminservice.dto;

import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Admin view of each paying merchant's standing: paid up, due soon, overdue or lapsed. */
public final class AdminBillingDtos {

    private AdminBillingDtos() {
    }

    public enum Standing {
        /** Current period is paid and the next payment is not close. */
        PAID,
        /** Current period ends within the chosen window. */
        DUE_SOON,
        /** A renewal failed or the period ended without the renewal arriving. */
        OVERDUE,
        /** The package ended and was not renewed. */
        LAPSED
    }

    public enum Sort {
        DUE_ASC,
        DUE_DESC,
        AMOUNT_DESC,
        LAST_PAID_DESC
    }

    public record AccountRow(
            Long merchantId,
            String merchantName,
            String email,
            String phone,
            Long purchaseId,
            String packageCode,
            String packageName,
            BillingPeriod billingPeriod,
            PaymentStyle paymentStyle,
            boolean autoRenew,
            boolean cancelAtPeriodEnd,
            Standing standing,
            SubscriptionStatus subscriptionStatus,
            BigDecimal renewalAmount,
            BigDecimal outstandingAmount,
            String currency,
            LocalDateTime lastPaidAt,
            LocalDateTime periodEndsAt,
            Long daysUntilDue,
            LocalDateTime graceEndsAt,
            String cardBrand,
            String cardLastFour,
            Long collectionCaseId,
            CollectionStatus collectionStatus,
            LocalDateTime collectionStatusAt
    ) {
    }

    public record Summary(
            long paid,
            long dueSoon,
            long overdue,
            long lapsed,
            BigDecimal dueSoonAmount,
            BigDecimal overdueAmount,
            int dueWithinDays,
            long openCollections
    ) {
    }

    public record AccountPage(
            List<AccountRow> content,
            Summary summary,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record CollectionEventResponse(
            Long id,
            CollectionStatus fromStatus,
            CollectionStatus toStatus,
            String note,
            String actor,
            LocalDateTime createdAt
    ) {
    }

    public record CollectionCaseResponse(
            Long id,
            Long merchantId,
            Long purchaseId,
            CollectionStatus status,
            BigDecimal amount,
            String currency,
            LocalDateTime dueSince,
            LocalDateTime openedAt,
            LocalDateTime statusAt,
            LocalDateTime closedAt,
            List<CollectionEventResponse> events
    ) {
    }

    public record OpenCollectionRequest(
            @NotNull Long merchantId,
            @Size(max = 2000) String note
    ) {
    }

    /** A status change, a note, or both; status may be left out to add a note only. */
    public record CollectionEventRequest(
            CollectionStatus status,
            @Size(max = 2000) String note
    ) {
    }
}
