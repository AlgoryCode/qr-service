package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tbl_menu_order", indexes = {
        @Index(name = "idx_menu_order_menu_status", columnList = "menu_id, status"),
        @Index(name = "idx_menu_order_customer_menu", columnList = "customer_id, menu_id"),
        @Index(name = "idx_menu_order_table_session", columnList = "table_session_id"),
        @Index(name = "idx_menu_order_submitted_at", columnList = "submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_session_id", nullable = false, columnDefinition = "uuid")
    private UUID tableSessionId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuOrderStatus status;

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

    @Column(name = "waiter_id")
    private Long waiterId;

    @Column(name = "waiter_note", columnDefinition = "text")
    private String waiterNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MenuOrderItem> items = new ArrayList<>();

    public void addItem(MenuOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void clearItems() {
        items.forEach(item -> item.setOrder(null));
        items.clear();
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
        if (status == null) {
            status = MenuOrderStatus.DRAFT;
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
