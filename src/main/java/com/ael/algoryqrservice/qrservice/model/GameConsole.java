package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** A rentable device (console, PC, VR, table...). A null rate falls back to the type rate, then the branch default. */
@Entity
@Table(name = "tbl_game_console")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GameConsole extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "hourly_rate", precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    @lombok.Builder.Default
    private DeviceType deviceType = DeviceType.PLAYSTATION;

    /** Rate for a 4-controller session; null falls back to the type rate, then the branch default. */
    @Column(name = "multi_hourly_rate", precision = 12, scale = 2)
    private BigDecimal multiHourlyRate;

    /** Key in the device's QR link; the customer sees the running session with it, without signing in. */
    @Column(name = "public_token", nullable = false, length = 40)
    private String publicToken;

    /** Out of service: cannot be started, moved to or reserved. */
    @Column(name = "maintenance", nullable = false)
    private boolean maintenance;

    @Column(name = "maintenance_note", length = 255)
    private String maintenanceNote;

    @Column(name = "maintenance_since")
    private LocalDateTime maintenanceSince;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "purchased_on")
    private LocalDate purchasedOn;
}
