package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fulfillment_detail", indexes = {
        @Index(name = "idx_fd_fulfillment_id", columnList = "fulfillment_id"),
        @Index(name = "idx_fd_user_scope", columnList = "user_id, scope_code"),
        @Index(name = "idx_fd_user_feature", columnList = "user_id, feature_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FulfillmentDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_id", nullable = false)
    private Long fulfillmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type_id", length = 32)
    private ProductType productTypeId;

    @Column(name = "feature_code", nullable = false, length = 64)
    private String featureCode;

    @Column(name = "scope_code", length = 64)
    private String scopeCode;

    @Column(name = "quantity", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer quantity = 0;

    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean unlimited = false;

    @Column(name = "used_quantity", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer usedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FulfillmentDetailSource source;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public int remainingQuantity() {
        if (unlimited) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, quantity - usedQuantity);
    }
}
