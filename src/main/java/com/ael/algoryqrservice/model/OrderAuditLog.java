package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.OrderAuditAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_order_audit_log", indexes = {
        @Index(name = "idx_order_audit_order", columnList = "order_id, created_at"),
        @Index(name = "idx_order_audit_menu", columnList = "menu_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "waiter_id")
    private Long waiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderAuditAction action;

    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
