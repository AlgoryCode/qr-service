package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.BillAdjustmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_bill_adjustment", indexes = {
        @Index(name = "idx_bill_adjustment_bill", columnList = "bill_id, created_at"),
        @Index(name = "idx_bill_adjustment_menu", columnList = "menu_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BillAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "waiter_id")
    private Long waiterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 16)
    private BillAdjustmentType adjustmentType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 64)
    private String reason;

    @Column(name = "reason_note", columnDefinition = "text")
    private String reasonNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
    }
}
