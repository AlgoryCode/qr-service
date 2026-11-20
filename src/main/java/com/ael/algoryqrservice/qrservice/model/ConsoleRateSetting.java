package com.ael.algoryqrservice.qrservice.model;

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

/** A branch's device rental settings: default rates, minimum and rounding, loyalty rule. */
@Entity
@Table(name = "tbl_console_rate_setting")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConsoleRateSetting extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id", nullable = false, unique = true)
    private Long branchId;

    @Column(name = "default_hourly_rate", precision = 12, scale = 2)
    private BigDecimal defaultHourlyRate;

    @Column(name = "default_multi_hourly_rate", precision = 12, scale = 2)
    private BigDecimal defaultMultiHourlyRate;

    /** Shortest time charged; 0 charges what was played. */
    @Column(name = "minimum_minutes", nullable = false)
    private int minimumMinutes;

    /** Played minutes are rounded up to a multiple of this; 1 charges by the minute. */
    @Column(name = "rounding_minutes", nullable = false)
    @lombok.Builder.Default
    private int roundingMinutes = 1;

    /** Every this many paid minutes earn {@link #loyaltyRewardMinutes} free minutes; null turns loyalty off. */
    @Column(name = "loyalty_every_minutes")
    private Integer loyaltyEveryMinutes;

    @Column(name = "loyalty_reward_minutes")
    private Integer loyaltyRewardMinutes;
}
