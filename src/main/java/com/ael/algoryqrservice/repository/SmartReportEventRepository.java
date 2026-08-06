package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.SmartReportEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SmartReportEventRepository extends JpaRepository<SmartReportEvent, UUID> {

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime createdAt);

    Page<SmartReportEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<SmartReportEvent> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            String status,
            Pageable pageable
    );

    Optional<SmartReportEvent> findByProcessIdAndUserId(UUID processId, Long userId);
}
