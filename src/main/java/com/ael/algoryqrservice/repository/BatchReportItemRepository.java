package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.BatchReportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BatchReportItemRepository extends JpaRepository<BatchReportItem, UUID> {

    List<BatchReportItem> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
