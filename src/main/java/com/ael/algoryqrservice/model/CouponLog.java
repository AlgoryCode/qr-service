package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.CouponLogAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_coupon_log", indexes = {
        @Index(name = "idx_coupon_log_coupon_id", columnList = "coupon_id"),
        @Index(name = "idx_coupon_log_purchase_id", columnList = "purchase_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CouponLogAction action;

    @Column(name = "list_price", precision = 12, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "payable", precision = 12, scale = 2)
    private BigDecimal payable;

    @Column(nullable = false, length = 1000)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
