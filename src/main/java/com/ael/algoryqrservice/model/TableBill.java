package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tbl_table_bill", indexes = {
        @Index(name = "idx_table_bill_menu_status", columnList = "menu_id, status"),
        @Index(name = "idx_table_bill_table_status", columnList = "table_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TableBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_session_id", columnDefinition = "uuid")
    private UUID tableSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private TableBillStatus status = TableBillStatus.OPEN;

    @Column(name = "opened_by_waiter_id")
    private Long openedByWaiterId;

    @Column(name = "closed_by_waiter_id")
    private Long closedByWaiterId;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 10)
    private TableBillPaymentMethod paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    @ColumnDefault("'TRY'")
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "tip_amount", precision = 12, scale = 2)
    private BigDecimal tipAmount;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "commission_settled_at")
    private LocalDateTime commissionSettledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TableBillItem> items = new ArrayList<>();

    public void addItem(TableBillItem item) {
        items.add(item);
        item.setBill(this);
    }

    public void removeItem(TableBillItem item) {
        items.remove(item);
        item.setBill(null);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (openedAt == null) {
            openedAt = now;
        }
        if (status == null) {
            status = TableBillStatus.OPEN;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
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
