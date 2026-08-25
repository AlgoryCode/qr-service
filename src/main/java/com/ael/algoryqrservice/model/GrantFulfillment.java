package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fulfillment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fulfillment_purchase_id", columnNames = "purchase_id")
}, indexes = {
        @Index(name = "idx_fulfillment_user_id", columnList = "user_id"),
        @Index(name = "idx_fulfillment_package_id", columnList = "package_id"),
        @Index(name = "idx_fulfillment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantFulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "purchase_id", nullable = false, unique = true)
    private Long purchaseId;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private GrantFulfillmentStatus status = GrantFulfillmentStatus.ACTIVE;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "migration_key", length = 128)
    private String migrationKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
