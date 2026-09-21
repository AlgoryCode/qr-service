package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.ProductBillingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A module line attached to a purchase: the optional catalog products the buyer added on
 * top of the package base price and paid for in the same transaction. Prices are frozen at
 * checkout so later catalog edits do not rewrite history.
 */
@Entity
@Table(name = "tbl_purchase_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_item_product", columnNames = {"purchase_id", "product_id"})
}, indexes = {
        @Index(name = "idx_purchase_item_purchase_id", columnList = "purchase_id"),
        @Index(name = "idx_purchase_item_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "line_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "line_vat", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineVat;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 16)
    @Builder.Default
    private ProductBillingType billingType = ProductBillingType.RECURRING;

    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean unlimited = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
