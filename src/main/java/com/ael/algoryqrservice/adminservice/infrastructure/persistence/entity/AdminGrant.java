package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A module or credit an admin gave a member for free, backed by a zero-amount add-on purchase. */
@Entity
@Table(name = "admin_grant")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    private Integer days;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "granted_by", nullable = false, length = 128)
    private String grantedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
