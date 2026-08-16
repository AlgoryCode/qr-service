package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.CampaignManualGrantAction;
import com.ael.algoryqrservice.model.enums.CampaignStatus;
import com.ael.algoryqrservice.model.enums.CampaignRewardStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateResponse {
        private String code;
        private String name;
        private String description;
        private String icon;
        private Map<String, Object> configSchema;
        private int sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignResponse {
        private Long id;
        private Long menuId;
        private String templateCode;
        private String name;
        private String slogan;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private CampaignStatus status;
        private Map<String, Object> config;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCampaignRequest {
        @NotBlank
        private String templateCode;
        @NotBlank
        private String name;
        private String slogan;
        @NotNull
        private LocalDateTime startsAt;
        @NotNull
        private LocalDateTime endsAt;
        @NotNull
        private Map<String, Object> config;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCampaignRequest {
        private String name;
        private String slogan;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Map<String, Object> config;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveCampaignResponse {
        private Long id;
        private String templateCode;
        private String name;
        private String slogan;
        private Map<String, Object> config;
        private List<Long> targetProductIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewItemRequest {
        @NotNull
        private Long productId;
        @NotNull
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewRequest {
        private List<PreviewItemRequest> items;
        private Long customerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignPreviewLine {
        private Long campaignId;
        private String campaignName;
        private String templateCode;
        private int campaignProductCount;
        private int pendingStamps;
        private int currentStamps;
        private int requiredStamps;
        private BigDecimal pendingSpend;
        private BigDecimal currentSpend;
        private BigDecimal thresholdAmount;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewResponse {
        private List<CampaignPreviewLine> lines;
        private int totalCampaignProducts;
        private boolean loggedIn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceRewardResponse {
        private boolean autoAssigned;
        private Long rewardId;
        private String claimToken;
        private String claimUrl;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimInfoResponse {
        private String status;
        private String campaignName;
        private String message;
        private boolean requiresLogin;
        private boolean alreadyClaimed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimResultResponse {
        private Long rewardId;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerProgressResponse {
        private Long campaignId;
        private String campaignName;
        private String templateCode;
        private int currentStamps;
        private int requiredStamps;
        private BigDecimal currentSpend;
        private BigDecimal thresholdAmount;
        private boolean rewardReady;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterCustomerLookupResponse {
        private Long customerId;
        private String firstName;
        private String lastName;
        private String email;
        private boolean member;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualGrantRequest {
        @NotBlank
        @Email
        private String email;
        @NotNull
        private Long campaignId;
        @NotNull
        private CampaignManualGrantAction action;
        private Integer quantity;
        private Long orderId;
        @NotBlank
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualGrantResponse {
        private String message;
        private int currentStamps;
        private int requiredStamps;
        private Long rewardId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCampaignSummary {
        private int campaignProductCount;
        private boolean guestOrder;
        private boolean rewardEligible;
        private String hint;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WinnerResponse {
        private Long rewardId;
        private Long customerId;
        private String firstName;
        private String lastName;
        private String email;
        private CampaignRewardStatus status;
        private LocalDateTime issuedAt;
        private LocalDateTime redeemedAt;
        private Long orderId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WinnerPageResponse {
        private List<WinnerResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }
}
