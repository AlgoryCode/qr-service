package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.CampaignRewardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_campaign_reward", indexes = {
        @Index(name = "idx_campaign_reward_customer", columnList = "customer_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CampaignReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "progress_id")
    private Long progressId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reward_payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String rewardPayload = "{}";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CampaignRewardStatus status = CampaignRewardStatus.AVAILABLE;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "redeemed_order_id")
    private Long redeemedOrderId;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
        if (rewardPayload == null || rewardPayload.isBlank()) {
            rewardPayload = "{}";
        }
    }
}
