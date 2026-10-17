package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.MenuReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface MenuReservationRepository
        extends JpaRepository<MenuReservation, Long>, JpaSpecificationExecutor<MenuReservation> {

    long countByMenuIdAndIpAddressAndCreatedAtAfter(Long menuId, String ipAddress, LocalDateTime after);
}
