package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_addresses", indexes = {
        @Index(name = "idx_customer_address_customer", columnList = "customer_id, is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Column(name = "address_text", nullable = false, columnDefinition = "text")
    private String addressText;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(name = "building_no", length = 32)
    private String buildingNo;

    @Column(name = "floor_no", length = 32)
    private String floorNo;

    @Column(name = "door_no", length = 32)
    private String doorNo;

    @Column(columnDefinition = "text")
    private String directions;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean defaultAddress = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean deleted = false;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
