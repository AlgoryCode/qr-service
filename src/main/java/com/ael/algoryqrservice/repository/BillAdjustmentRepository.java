package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.BillAdjustment;
import com.ael.algoryqrservice.model.enums.BillAdjustmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BillAdjustmentRepository extends JpaRepository<BillAdjustment, Long> {

    List<BillAdjustment> findByMenuIdInAndCreatedAtBetween(
            Collection<Long> menuIds,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
            SELECT COALESCE(SUM(a.amount), 0) FROM BillAdjustment a
            WHERE a.menuId IN :menuIds
              AND a.adjustmentType = :type
              AND a.createdAt >= :fromDt
              AND a.createdAt <= :toDt
            """)
    BigDecimal sumByType(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("type") BillAdjustmentType type,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );
}
