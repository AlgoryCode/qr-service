package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationPendingProductRepository extends JpaRepository<IntegrationPendingProduct, UUID> {

    Page<IntegrationPendingProduct> findByMenuIdAndApprovalStatusOrderByCreatedAtAsc(
            Long menuId,
            String approvalStatus,
            Pageable pageable
    );

    Optional<IntegrationPendingProduct> findByJobIdAndSourceProductId(UUID jobId, String sourceProductId);

    boolean existsByJobIdAndSourceProductId(UUID jobId, String sourceProductId);

    List<IntegrationPendingProduct> findByMenuIdAndIdIn(Long menuId, Collection<UUID> ids);
}
