package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.MenuProductOptionGroupKind;
import com.ael.algoryqrservice.model.enums.MenuProductOptionUnit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_menu_product_option_group", indexes = {
        @Index(name = "idx_menu_product_option_group_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuProductOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @ColumnDefault("'CUSTOM'")
    @Builder.Default
    private MenuProductOptionGroupKind kind = MenuProductOptionGroupKind.CUSTOM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private MenuProductOptionUnit unit = MenuProductOptionUnit.NONE;

    @Column(name = "min_select", nullable = false)
    @Builder.Default
    private int minSelect = 0;

    @Column(name = "max_select", nullable = false)
    @Builder.Default
    private int maxSelect = 1;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<MenuProductOption> options = new ArrayList<>();

    public void addOption(MenuProductOption option) {
        options.add(option);
        option.setGroup(this);
    }

    public void clearOptions() {
        options.forEach(option -> option.setGroup(null));
        options.clear();
    }
}
