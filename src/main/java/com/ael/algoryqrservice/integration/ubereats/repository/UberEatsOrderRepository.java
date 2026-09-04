package com.ael.algoryqrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface UberEatsOrderRepository
        extends JpaRepository<UberEatsOrder, Long>, JpaSpecificationExecutor<UberEatsOrder> {

    Optional<UberEatsOrder> findByConnectionIdAndExternalOrderId(Long connectionId, String externalOrderId);

    Optional<UberEatsOrder> findByIdAndConnectionId(Long id, Long connectionId);

    Page<UberEatsOrder> findByConnectionIdOrderByPackageCreatedAtDesc(Long connectionId, Pageable pageable);

    Page<UberEatsOrder> findByConnectionIdAndPackageStatusIgnoreCaseOrderByPackageCreatedAtDesc(
            Long connectionId,
            String packageStatus,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM UberEatsOrder o
            WHERE o.connectionId = :connectionId
              AND o.packageCreatedAt BETWEEN :from AND :to
              AND LOWER(o.packageStatus) IN :statuses
            """)
    BigDecimal sumRevenueByConnectionAndStatuses(
            @Param("connectionId") Long connectionId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("statuses") Collection<String> statuses
    );
}
