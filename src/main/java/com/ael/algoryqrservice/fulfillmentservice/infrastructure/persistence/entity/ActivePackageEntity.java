package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "active_package")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ActivePackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false, unique = true)
    private Long merchantId;

    @Column(name = "fulfillment_id", nullable = false)
    private Long fulfillmentId;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_code", length = 128)
    private String packageCode;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PackageStatus status;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
