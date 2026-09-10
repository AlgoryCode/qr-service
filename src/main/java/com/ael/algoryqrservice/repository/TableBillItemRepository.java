package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableBillItemRepository extends JpaRepository<TableBillItem, Long> {

    Optional<TableBillItem> findByIdAndBillId(Long id, Long billId);

    List<TableBillItem> findByBillId(Long billId);

    @Query("""
            SELECT bi.bill.id, bi.productId, bi.productName
            FROM TableBillItem bi
            JOIN bi.bill b
            WHERE b.menuId IN :menuIds
              AND b.status = :status
              AND b.closedAt >= :fromDt
              AND b.closedAt <= :toDt
            """)
    List<Object[]> findProductRowsForClosedBills(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("status") TableBillStatus status,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt
    );
}
