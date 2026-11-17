package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A frozen copy of a package's lines and prices. Never edited: changing the package opens a new
 * version, and purchases keep the one they were bought on.
 */
@Entity
@Table(name = "plan_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"package_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "yearly_price", precision = 12, scale = 2)
    private BigDecimal yearlyPrice;

    @Column(name = "monthly_discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal monthlyDiscount = BigDecimal.ZERO;

    @Column(name = "yearly_discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal yearlyDiscount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "validity_days", nullable = false)
    private Integer validityDays;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "planVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlanVersionItem> items = new ArrayList<>();
}
