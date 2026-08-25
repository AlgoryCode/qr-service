package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_product", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "scope_code", nullable = false, length = 64)
    private String scopeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_id", length = 32)
    private ProductType typeId;

    @Column(name = "feature_code", length = 64)
    private String featureCode;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    @ColumnDefault("20.00")
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("20.00");

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean consumable = true;

    @Column(name = "addon_purchasable", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean addonPurchasable = false;

    @Column(name = "requires_count_sync", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean requiresCountSync = false;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
