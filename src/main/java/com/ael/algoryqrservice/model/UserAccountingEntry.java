package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_user_accounting_entry", indexes = {
        @Index(name = "idx_user_accounting_entry_user_occurred", columnList = "user_id, occurred_at"),
        @Index(name = "idx_user_accounting_entry_user_type_occurred", columnList = "user_id, entry_type, occurred_at"),
        @Index(name = "idx_user_accounting_entry_menu", columnList = "menu_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserAccountingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private AccountingEntryType entryType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    @ColumnDefault("'TRY'")
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 500)
    private String note;

    @Column(name = "menu_id")
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private AccountingSourceType sourceType = AccountingSourceType.MANUAL;

    @Column(name = "source_bill_id")
    private Long sourceBillId;

    @Column(name = "source_order_id")
    private Long sourceOrderId;

    @Column(name = "created_by_waiter_id")
    private Long createdByWaiterId;

    @Column(name = "order_amount", precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "tip_amount", precision = 12, scale = 2)
    private BigDecimal tipAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
