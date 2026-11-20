package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalTime;

/** A time-of-day tariff: on the days in {@code daysMask} (bit 0 = Monday) between start and end it adds {@code adjustPercent} to the rate. An end at or before the start runs past midnight. */
@Entity
@Table(name = "tbl_device_tariff_rule")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceTariffRule extends QrBaseModel {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "days_mask", nullable = false)
    private int daysMask;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "adjust_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal adjustPercent;
}
