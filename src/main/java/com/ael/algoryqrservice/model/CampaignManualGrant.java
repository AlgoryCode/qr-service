package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.CampaignManualGrantAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_campaign_manual_grant", indexes = {
        @Index(name = "idx_campaign_manual_grant_menu", columnList = "menu_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CampaignManualGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "waiter_id", nullable = false)
    private Long waiterId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignManualGrantAction action;

    private Integer quantity;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
