package com.ael.algoryqrservice.integration.trendyolgo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_trendyol_go_order", indexes = {
        @Index(name = "idx_tgo_order_connection_external", columnList = "connection_id, external_order_id", unique = true),
        @Index(name = "idx_tgo_order_connection_status", columnList = "connection_id, package_status"),
        @Index(name = "idx_tgo_order_created", columnList = "package_created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrendyolGoOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "external_order_id", nullable = false, length = 128)
    private String externalOrderId;

    @Column(name = "package_status", length = 64)
    private String packageStatus;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 8)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_phone", length = 64)
    private String customerPhone;

    @Column(name = "delivery_address", columnDefinition = "text")
    private String deliveryAddress;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "package_created_at")
    private LocalDateTime packageCreatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", columnDefinition = "jsonb")
    private String itemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (currency == null || currency.isBlank()) {
            currency = "TRY";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
