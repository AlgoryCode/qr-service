package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_bill_payment", indexes = {
        @Index(name = "idx_bill_payment_bill", columnList = "bill_id"),
        @Index(name = "idx_bill_payment_paid_at", columnList = "paid_at"),
        @Index(name = "idx_bill_payment_bill_item", columnList = "bill_item_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private TableBill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_item_id")
    private TableBillItem billItem;

    @Column(name = "waiter_id")
    private Long waiterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private TableBillPaymentMethod paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "quantity_paid", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int quantityPaid = 0;

    @Column(name = "is_tip", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean tip = false;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (paidAt == null) {
            paidAt = now;
        }
    }
}
