package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.SmartReportEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SmartReportEventRepository extends JpaRepository<SmartReportEvent, UUID> {

    long countByMerchantIdAndCreatedAtGreaterThanEqual(Long merchantId, LocalDateTime createdAt);

    Page<SmartReportEvent> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    Page<SmartReportEvent> findByMerchantIdAndStatusOrderByCreatedAtDesc(
            Long merchantId,
            String status,
            Pageable pageable
    );

    Optional<SmartReportEvent> findByProcessIdAndMerchantId(UUID processId, Long merchantId);
}
