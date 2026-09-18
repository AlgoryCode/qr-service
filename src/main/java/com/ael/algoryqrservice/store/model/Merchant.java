package com.ael.algoryqrservice.store.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "merchants", indexes = {
        @Index(name = "idx_merchant_branch", columnList = "branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "catalog_menu_id", nullable = false)
    private Long catalogMenuId;

    @Column(name = "store_no", nullable = false)
    private Long storeNo;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(name = "public_token", nullable = false, length = 32)
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.DRAFT;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_office", length = 120)
    private String taxOffice;

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(length = 32)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "free_delivery_threshold", precision = 12, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "avg_prep_minutes", nullable = false)
    @ColumnDefault("30")
    @Builder.Default
    private int avgPrepMinutes = 30;

    @Column(name = "delivery_radius_km", precision = 6, scale = 2)
    private BigDecimal deliveryRadiusKm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_types", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Set<StoreDeliveryType> deliveryTypes = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_methods", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Set<StorePaymentMethod> paymentMethods = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_hours", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<StoreWorkingHour> workingHours = new ArrayList<>();

    @Column(name = "manually_closed", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean manuallyClosed = false;

    @Column(name = "order_counter", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private long orderCounter = 0L;

    @Column(nullable = false, length = 8)
    @ColumnDefault("'TRY'")
    @Builder.Default
    private String currency = "TRY";

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
