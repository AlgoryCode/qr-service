package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_menu_product_visit", indexes = {
        @Index(name = "idx_menu_product_visit_menu_id", columnList = "menu_id"),
        @Index(name = "idx_menu_product_visit_product_id", columnList = "menu_product_id"),
        @Index(name = "idx_menu_product_visit_visited_at", columnList = "visited_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuProductVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "menu_product_id", nullable = false)
    private Long menuProductId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "device_type", nullable = false, length = 10)
    private String deviceType;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;
}
