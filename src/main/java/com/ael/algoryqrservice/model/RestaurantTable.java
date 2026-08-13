package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_restaurant_table", indexes = {
        @Index(name = "idx_restaurant_table_menu_id", columnList = "menu_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = "public_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "table_number")
    private Integer tableNumber;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Column(name = "qr_image_base64", columnDefinition = "text")
    private String qrImageBase64;

    @Column(nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
