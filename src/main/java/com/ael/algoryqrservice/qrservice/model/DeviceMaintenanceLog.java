package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceMaintenanceKind;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
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

/** A repair or downtime of a device, or a one-off cost such as a new controller. */
@Entity
@Table(name = "tbl_device_maintenance_log")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceMaintenanceLog extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "console_id", nullable = false)
    private Long consoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private DeviceMaintenanceKind kind;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "staff_id")
    private Long staffId;
}
