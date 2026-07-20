package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_plan_change", indexes = {
        @Index(name = "idx_plan_change_user_id", columnList = "user_id"),
        @Index(name = "idx_plan_change_status_effective", columnList = "status, effective_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_purchase_id", nullable = false)
    private Long fromPurchaseId;

    @Column(name = "from_package_id", nullable = false)
    private Long fromPackageId;

    @Column(name = "to_package_id", nullable = false)
    private Long toPackageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanChangeDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanChangeTiming timing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanChangeStatus status;

    @Column(name = "charge_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargeAmount;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "payment_conversation_id", length = 128)
    private String paymentConversationId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "resulting_purchase_id")
    private Long resultingPurchaseId;

    @Column(name = "warning_ack", nullable = false)
    @Builder.Default
    private boolean warningAck = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
