package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.GoogleAuthHandoffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GoogleAuthHandoffRepository extends JpaRepository<GoogleAuthHandoffEntity, UUID> {

    Optional<GoogleAuthHandoffEntity> findByTicketHash(String ticketHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GoogleAuthHandoffEntity ticket
            set ticket.consumed = true, ticket.consumedAt = :consumedAt
            where ticket.ticketHash = :ticketHash
              and ticket.consumed = false
              and ticket.expiresAt > :consumedAt
            """)
    int consume(@Param("ticketHash") String ticketHash, @Param("consumedAt") LocalDateTime consumedAt);
}
