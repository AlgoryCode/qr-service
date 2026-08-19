package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_menu_fixed_expense", indexes = {
        @Index(name = "idx_menu_fixed_expense_menu", columnList = "menu_id, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuFixedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "daily_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyAmount;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean active = true;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
