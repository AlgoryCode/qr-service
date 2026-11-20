package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.LocalTime;
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

/** A fixed-price block of play time; time beyond it is charged at the normal rate. */
@Entity
@Table(name = "tbl_device_package")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DevicePackage extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Null: every device type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Bit 0 = Monday. */
    @Column(name = "days_mask", nullable = false)
    @lombok.Builder.Default
    private int daysMask = 127;

    /** Optional window in which the package can be started. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;
}
