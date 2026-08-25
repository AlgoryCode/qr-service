package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.model.enums.FulfillmentUsageAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fulfillment_usage_log", indexes = {
        @Index(name = "idx_ful_detail_id", columnList = "detail_id"),
        @Index(name = "idx_ful_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FulfillmentUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "detail_id", nullable = false)
    private Long detailId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FulfillmentUsageAction action;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 16)
    private FulfillmentReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
