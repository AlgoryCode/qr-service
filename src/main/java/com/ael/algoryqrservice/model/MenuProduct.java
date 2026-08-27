package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "sub_category_id", nullable = false)
    private Long subCategoryId;

    @Column(name = "descriptor_category_id")
    private Long descriptorCategoryId;

    @Column(nullable = false)
    private int sortOrder;

    private String imageUrl;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "chef_recommended", nullable = false)
    @Builder.Default
    private boolean chefRecommended = false;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private long ratingCount = 0L;

    @Column(name = "serves_people_min")
    private Integer servesPeopleMin;

    @Column(name = "serves_people_max")
    private Integer servesPeopleMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private NutritionFacts nutrition;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tbl_menu_product_tag", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag_id")
    @Builder.Default
    private Set<Long> tagIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tbl_menu_product_allergen", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "allergen_id")
    @Builder.Default
    private Set<Long> allergenIds = new HashSet<>();
}
