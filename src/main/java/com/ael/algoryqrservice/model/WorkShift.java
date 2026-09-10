package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.WorkShiftStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tbl_work_shift", indexes = {
        @Index(name = "idx_work_shift_branch_status", columnList = "branch_id, status"),
        @Index(name = "idx_work_shift_opened_at", columnList = "branch_id, opened_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "opened_by_waiter_id", nullable = false)
    private Long openedByWaiterId;

    @Column(name = "closed_by_waiter_id")
    private Long closedByWaiterId;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opening_float", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "closing_cash", precision = 12, scale = 2)
    private BigDecimal closingCash;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private WorkShiftStatus status = WorkShiftStatus.OPEN;

    @ElementCollection
    @CollectionTable(name = "tbl_work_shift_waiter", joinColumns = @JoinColumn(name = "shift_id"))
    @Column(name = "waiter_id")
    @Builder.Default
    private Set<Long> waiterIds = new HashSet<>();

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
        if (openedAt == null) {
            openedAt = now;
        }
        if (status == null) {
            status = WorkShiftStatus.OPEN;
        }
        if (openingFloat == null) {
            openingFloat = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
