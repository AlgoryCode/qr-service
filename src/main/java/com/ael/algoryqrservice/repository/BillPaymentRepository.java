package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    @Query("""
            SELECT p FROM BillPayment p
            JOIN FETCH p.bill b
            LEFT JOIN FETCH p.billItem
            WHERE b.menuId = :menuId
              AND p.paidAt >= :fromDt
              AND p.paidAt <= :toDt
            ORDER BY p.paidAt ASC
            """)
    List<BillPayment> findByMenuIdAndPaidAtBetween(
            @Param("menuId") Long menuId,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );

    List<BillPayment> findByBillIdOrderByPaidAtAsc(Long billId);

    boolean existsByBillIdAndSplitShareNumber(Long billId, Integer splitShareNumber);
}
