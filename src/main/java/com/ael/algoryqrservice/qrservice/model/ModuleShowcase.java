package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Store presentation of an add-on module: tagline, badge, order and visibility. */
@Entity
@Table(name = "module_showcase")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleShowcase {

    public enum Badge { NEW, POPULAR, DEAL }

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(length = 160)
    private String tagline;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Badge badge;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
