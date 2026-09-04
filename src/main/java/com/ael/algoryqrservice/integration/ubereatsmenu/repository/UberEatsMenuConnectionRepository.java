package com.ael.algoryqrservice.integration.ubereatsmenu.repository;

import com.ael.algoryqrservice.integration.ubereatsmenu.model.UberEatsMenuConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsMenuConnectionRepository extends JpaRepository<UberEatsMenuConnection, Long> {

    List<UberEatsMenuConnection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UberEatsMenuConnection> findByUserIdAndMenuId(Long userId, Long menuId);

    Optional<UberEatsMenuConnection> findByMenuId(Long menuId);
}
