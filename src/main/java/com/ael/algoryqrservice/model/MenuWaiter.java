package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_menu_waiter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_menu_waiter_username", columnNames = "username")
}, indexes = {
        @Index(name = "idx_menu_waiter_branch", columnList = "branch_id"),
        @Index(name = "idx_menu_waiter_owner", columnList = "owner_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuWaiter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean active = true;

    @Column(name = "commission_enabled", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean commissionEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", length = 10)
    private WaiterCommissionType commissionType;

    @Column(name = "commission_value", precision = 12, scale = 2)
    private BigDecimal commissionValue;

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
