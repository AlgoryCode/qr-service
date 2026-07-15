package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "tbl_purchase_fulfillment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fulfillment_purchase_installment", columnNames = {"purchase_id", "installment_id"}),
        @UniqueConstraint(name = "uk_fulfillment_event", columnNames = "event_id")
}, indexes = {
        @Index(name = "idx_fulfillment_purchase", columnList = "purchase_id"),
        @Index(name = "idx_fulfillment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseFulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "installment_id", nullable = false, length = 128)
    private String installmentId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FulfillmentStatus status;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
