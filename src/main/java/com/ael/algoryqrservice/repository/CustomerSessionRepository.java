package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {

    Optional<CustomerSession> findByIdAndCustomerId(UUID id, Long customerId);
}
