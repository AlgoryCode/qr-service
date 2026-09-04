package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.AiMenuImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMenuImportJobRepository extends JpaRepository<AiMenuImportJob, UUID> {

    Optional<AiMenuImportJob> findByIdAndMenuId(UUID id, Long menuId);

    List<AiMenuImportJob> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);
}
