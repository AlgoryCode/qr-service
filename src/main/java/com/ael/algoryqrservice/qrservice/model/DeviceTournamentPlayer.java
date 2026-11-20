package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** A player registered for a tournament. */
@Entity
@Table(name = "tbl_device_tournament_player")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DeviceTournamentPlayer extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private DevicePaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "staff_id")
    private Long staffId;
}
