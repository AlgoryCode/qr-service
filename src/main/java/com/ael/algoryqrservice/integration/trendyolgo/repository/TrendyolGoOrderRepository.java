package com.ael.algoryqrservice.integration.trendyolgo.repository;

import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TrendyolGoOrderRepository
        extends JpaRepository<TrendyolGoOrder, Long>, JpaSpecificationExecutor<TrendyolGoOrder> {

    Optional<TrendyolGoOrder> findByConnectionIdAndExternalOrderId(Long connectionId, String externalOrderId);

    Optional<TrendyolGoOrder> findByIdAndConnectionId(Long id, Long connectionId);

    Page<TrendyolGoOrder> findByConnectionIdOrderByPackageCreatedAtDesc(Long connectionId, Pageable pageable);

    Page<TrendyolGoOrder> findByConnectionIdAndPackageStatusIgnoreCaseOrderByPackageCreatedAtDesc(
            Long connectionId,
            String packageStatus,
            Pageable pageable
    );
}
