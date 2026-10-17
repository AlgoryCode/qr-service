package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.IntegrationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, UUID> {

    List<IntegrationJob> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);
}
