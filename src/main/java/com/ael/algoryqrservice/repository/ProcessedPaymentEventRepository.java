package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.ProcessedPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, Long> {

    boolean existsByEventId(String eventId);
}
