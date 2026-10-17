package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentEventOutboxJpaRepository extends JpaRepository<PaymentEventOutboxEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<PaymentEventOutboxEntity> findByEventId(String eventId);

    List<PaymentEventOutboxEntity> findByStatusAndNextAttemptAtLessThanEqualOrderById(
            String status,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );
}
