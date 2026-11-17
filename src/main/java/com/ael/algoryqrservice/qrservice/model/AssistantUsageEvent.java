package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageOutcome;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageSurface;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageTopic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One question a merchant asked an AI assistant from the dashboard. Holds no message text. */
@Entity
@Table(
        name = "tbl_assistant_usage_event",
        indexes = @Index(name = "idx_assistant_usage_merchant_created", columnList = "merchant_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantUsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssistantUsageSurface surface;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssistantUsageTopic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssistantUsageOutcome outcome;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
