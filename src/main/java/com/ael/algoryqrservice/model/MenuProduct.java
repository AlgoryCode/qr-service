package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_menu_products")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MenuProduct extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(nullable = false)
    private Long menuId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private BigDecimal price;

    @Column(nullable = false)
    private String currency = "TRY";

    private String category;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false)
    private int sortOrder;

    private String imageUrl;

    @Column(nullable = false)
    private boolean available = true;
}
