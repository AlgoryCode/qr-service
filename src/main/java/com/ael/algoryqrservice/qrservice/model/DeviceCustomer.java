package com.ael.algoryqrservice.qrservice.model;


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

/** A regular of the merchant: prepaid balance and loyalty minutes, shared by every branch. */
@Entity
@Table(name = "tbl_device_customer")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceCustomer extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "free_minutes", nullable = false)
    private int freeMinutes;

    @Column(name = "loyalty_progress_minutes", nullable = false)
    private int loyaltyProgressMinutes;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "total_spent", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSpent;

    /** Gift and referral money; spent as a discount, never booked as income. */
    @Column(name = "bonus_balance", nullable = false, precision = 12, scale = 2)
    @lombok.Builder.Default
    private BigDecimal bonusBalance = BigDecimal.ZERO.setScale(2);

    /** Code a friend gives when opening a card. */
    @Column(name = "referral_code", length = 16)
    private String referralCode;

    @Column(name = "referred_by_id")
    private Long referredById;

    /** The referrer has been paid for this customer's first session. */
    @Column(name = "referral_rewarded", nullable = false)
    private boolean referralRewarded;

    @Column(name = "last_visit_at")
    private LocalDateTime lastVisitAt;

    /** Finished sessions. */
    @Column(name = "visits", nullable = false)
    private int visits;
}
