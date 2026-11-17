package com.ael.algoryqrservice.qrservice.billing.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One unpaid debt being followed up; closed once paid, written off or cancelled. */
@Entity
@Table(name = "billing_collection_case")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CollectionStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    /** When the money became due: the end of the period that was not paid. */
    @Column(name = "due_since")
    private LocalDateTime dueSince;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "status_at", nullable = false)
    private LocalDateTime statusAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isOpen() {
        return closedAt == null;
    }
}
