package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_waiter_commission_record", indexes = {
        @Index(name = "idx_waiter_commission_waiter_created", columnList = "waiter_id, created_at"),
        @Index(name = "idx_waiter_commission_menu_created", columnList = "menu_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WaiterCommissionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "waiter_id", nullable = false)
    private Long waiterId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private WaiterCommissionRecordType recordType;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @Column(name = "commission_value_snapshot", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal commissionValueSnapshot = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    @ColumnDefault("'TRY'")
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (baseAmount == null) {
            baseAmount = BigDecimal.ZERO;
        }
        if (commissionValueSnapshot == null) {
            commissionValueSnapshot = BigDecimal.ZERO;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (currency == null || currency.isBlank()) {
            currency = "TRY";
        }
    }
}
