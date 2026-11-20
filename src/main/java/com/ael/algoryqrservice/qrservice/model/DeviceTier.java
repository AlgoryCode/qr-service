package com.ael.algoryqrservice.qrservice.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** A customer level reached by total spending; it gives a discount on play time. */
@Entity
@Table(name = "tbl_device_tier")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceTier extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "min_spent", nullable = false, precision = 12, scale = 2)
    private BigDecimal minSpent;

    @Column(name = "discount_percent", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountPercent;
}
