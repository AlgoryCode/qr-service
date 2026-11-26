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

/** One correction made to a finished session: who, why and what changed (one line per field in {@code summary}). */
@Entity
@Table(name = "tbl_console_session_change")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConsoleSessionChange extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "changed_by", length = 160)
    private String changedBy;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "old_amount", precision = 12, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", precision = 12, scale = 2)
    private BigDecimal newAmount;
}
