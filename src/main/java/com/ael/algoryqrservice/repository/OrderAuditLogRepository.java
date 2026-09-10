package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.OrderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderAuditLogRepository extends JpaRepository<OrderAuditLog, Long> {

    List<OrderAuditLog> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
