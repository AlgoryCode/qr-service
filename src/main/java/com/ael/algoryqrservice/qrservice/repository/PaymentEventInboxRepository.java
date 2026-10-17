package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PaymentEventInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventInboxRepository extends JpaRepository<PaymentEventInbox, Long> {

    boolean existsByEventId(String eventId);
}
