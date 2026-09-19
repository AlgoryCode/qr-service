package com.ael.algoryqrservice.store.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_merchant_store_order", indexes = {
        @Index(name = "idx_store_order_merchant_status", columnList = "merchant_id, status"),
        @Index(name = "idx_store_order_merchant_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_store_order_customer", columnList = "customer_id"),
        @Index(name = "idx_store_order_courier", columnList = "courier_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StoreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "public_token", nullable = false, length = 32)
    private String publicToken;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 16)
    private StoreDeliveryType deliveryType;

    @Column(name = "address_text", columnDefinition = "text")
    private String addressText;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(name = "building_no", length = 32)
    private String buildingNo;

    @Column(name = "floor_no", length = 32)
    private String floorNo;

    @Column(name = "door_no", length = 32)
    private String doorNo;

    @Column(columnDefinition = "text")
    private String directions;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StoreOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private StorePaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private StorePaymentStatus paymentStatus = StorePaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    @ColumnDefault("'TRY'")
    @Builder.Default
    private String currency = "TRY";

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "courier_id")
    private Long courierId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "preparing_at")
    private LocalDateTime preparingAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoreOrderItem> items = new ArrayList<>();

    public void addItem(StoreOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = StoreOrderStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
