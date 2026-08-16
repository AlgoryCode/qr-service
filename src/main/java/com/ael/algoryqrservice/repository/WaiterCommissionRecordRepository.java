package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaiterCommissionRecordRepository extends JpaRepository<WaiterCommissionRecord, Long> {

    List<WaiterCommissionRecord> findByWaiterIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long waiterId,
            LocalDateTime start,
            LocalDateTime end
    );

    Page<WaiterCommissionRecord> findByWaiterIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long waiterId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM WaiterCommissionRecord r
            WHERE r.waiterId = :waiterId
              AND r.createdAt >= :start
              AND r.createdAt <= :end
            """)
    BigDecimal sumAmountByWaiterAndCreatedAtBetween(
            @Param("waiterId") Long waiterId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
