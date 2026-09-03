package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.IntegrationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, UUID> {

    List<IntegrationJob> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);
}
