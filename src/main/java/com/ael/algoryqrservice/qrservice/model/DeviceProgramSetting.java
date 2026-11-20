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

/** Merchant-wide customer programme settings (customer cards are shared by every branch). */
@Entity
@Table(name = "tbl_device_program_setting")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceProgramSetting extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** Bonus for the referrer once the friend finishes a first session. */
    @Column(name = "referral_reward_amount", precision = 12, scale = 2)
    private BigDecimal referralRewardAmount;

    /** Bonus for the new customer when the card is opened with a code. */
    @Column(name = "referral_welcome_amount", precision = 12, scale = 2)
    private BigDecimal referralWelcomeAmount;
}
