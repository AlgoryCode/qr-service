package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.AiMenuImportDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMenuImportDraftRepository extends JpaRepository<AiMenuImportDraft, UUID> {

    Page<AiMenuImportDraft> findByMenuIdAndApprovalStatusOrderByCreatedAtAsc(
            Long menuId,
            String approvalStatus,
            Pageable pageable
    );

    Page<AiMenuImportDraft> findByMenuIdAndJobIdAndApprovalStatusOrderByCreatedAtAsc(
            Long menuId,
            UUID jobId,
            String approvalStatus,
            Pageable pageable
    );

    Optional<AiMenuImportDraft> findByIdAndMenuId(UUID id, Long menuId);

    List<AiMenuImportDraft> findByMenuIdAndIdIn(Long menuId, Collection<UUID> ids);

    boolean existsByJobIdAndSourceProductId(UUID jobId, String sourceProductId);
}
