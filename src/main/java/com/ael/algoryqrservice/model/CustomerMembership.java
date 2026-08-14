package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_customer_membership", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"customer_id", "menu_id"})
}, indexes = {
        @Index(name = "idx_customer_membership_menu_id", columnList = "menu_id"),
        @Index(name = "idx_customer_membership_customer_id", columnList = "customer_id"),
        @Index(name = "idx_customer_membership_business_id", columnList = "business_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CustomerMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}
