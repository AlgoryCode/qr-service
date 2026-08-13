package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuWaiterSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuWaiterSessionRepository extends JpaRepository<MenuWaiterSession, UUID> {

    Optional<MenuWaiterSession> findByIdAndWaiterId(UUID id, Long waiterId);
}
