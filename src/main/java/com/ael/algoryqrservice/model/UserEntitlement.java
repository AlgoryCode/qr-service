package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_user_entitlement", indexes = {
        @Index(name = "idx_user_entitlement_user_id", columnList = "user_id"),
        @Index(name = "idx_user_entitlement_purchase_id", columnList = "purchase_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_code", nullable = false)
    private ProductCode productCode;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "used_quantity", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer usedQuantity = 0;

    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean unlimited = false;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isExpiredByDate() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean hasRemaining() {
        return unlimited || remainingQuantity != null && remainingQuantity > 0;
    }

    public boolean isUsable(PurchaseStatus purchaseStatus) {
        return purchaseStatus == PurchaseStatus.ACTIVE
                && !isExpiredByDate()
                && hasRemaining();
    }
}
