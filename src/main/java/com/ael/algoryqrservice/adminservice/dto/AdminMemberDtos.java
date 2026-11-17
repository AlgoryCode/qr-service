package com.ael.algoryqrservice.adminservice.dto;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminMemberDtos {

    public static final int MAX_TAGS = 12;

    private AdminMemberDtos() {
    }

    /** Everything an admin needs about one member at a glance, beyond the profile itself. */
    public record Overview(
            Long merchantId,
            Standing standing,
            String packageName,
            LocalDateTime periodEndsAt,
            Long daysUntilDue,
            BigDecimal totalPaid,
            BigDecimal totalRefunded,
            int paidPurchases,
            String currency,
            LocalDateTime firstPaidAt,
            LocalDateTime lastPaidAt,
            Long collectionCaseId,
            CollectionStatus collectionStatus,
            List<String> tags,
            int noteCount,
            int activeGrants,
            List<Activity> activity
    ) {
    }

    /** One line of the member timeline: purchases, grants, notes and admin actions, newest first. */
    public record Activity(LocalDateTime at, String kind, String title, String detail, String actor) {
    }

    public record NoteResponse(Long id, String body, boolean pinned, String authorName,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String body, Boolean pinned) {
    }

    public record NoteUpdateRequest(@Size(max = 4000) String body, Boolean pinned) {
    }

    public record TagsRequest(@NotNull @Size(max = MAX_TAGS) List<@NotBlank @Size(max = 40) String> tags) {
    }

    public record TagCount(String tag, long count) {
    }

    public record GrantRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(10_000) Integer quantity,
            /** Null for one-time credits that stay until used. */
            @Min(1) @Max(3_650) Integer days,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record GrantResponse(Long id, Long purchaseId, Long productId, String productCode, String productName,
                                int quantity, Integer days, String reason, String grantedBy, LocalDateTime createdAt,
                                LocalDateTime expiresAt, LocalDateTime revokedAt, String revokedBy, boolean active) {
    }

    public record GrantableProduct(Long id, String code, String name, boolean consumable, String billingType,
                                   BigDecimal unitPrice) {
    }
}
