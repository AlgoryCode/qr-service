package com.ael.algoryqrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "tbl_stock_recipe_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_recipe_product_ingredient",
                columnNames = {"product_id", "ingredient_id"}
        ),
        indexes = {
                @Index(name = "idx_stock_recipe_product", columnList = "product_id"),
                @Index(name = "idx_stock_recipe_ingredient", columnList = "ingredient_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StockRecipeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "quantity_per_sale", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityPerSale;
}
