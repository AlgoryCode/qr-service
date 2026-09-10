package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.BatchReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchReportRepository extends JpaRepository<BatchReport, UUID> {

    Page<BatchReport> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BatchReport> findByIdAndUserId(UUID id, Long userId);

    List<BatchReport> findByStatusInOrderByUpdatedAtAsc(Collection<String> statuses);
}
