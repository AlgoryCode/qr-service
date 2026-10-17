package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.SmartReportResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SmartReportResultRepository extends JpaRepository<SmartReportResult, Long> {

    Optional<SmartReportResult> findByProcessId(UUID processId);
}
