package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FulfillmentUsageLogRepository extends JpaRepository<FulfillmentUsageLog, Long> {

    List<FulfillmentUsageLog> findByDetailIdOrderByCreatedAtDesc(Long detailId);

    Page<FulfillmentUsageLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
