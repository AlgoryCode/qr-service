package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.MenuReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_menu_reservation", indexes = {
        @Index(name = "idx_menu_reservation_menu_status_at", columnList = "menu_id, status, reservation_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(length = 40)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "reservation_at", nullable = false)
    private LocalDateTime reservationAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuReservationStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "device_type", nullable = false, length = 10)
    private String deviceType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
