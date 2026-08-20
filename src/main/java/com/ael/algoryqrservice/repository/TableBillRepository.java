package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableBillRepository extends JpaRepository<TableBill, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByMenuIdAndTableIdAndStatus(Long menuId, Long tableId, TableBillStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByIdAndMenuId(Long id, Long menuId);

    List<TableBill> findByMenuIdAndStatus(Long menuId, TableBillStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByIdAndMenuIdAndStatus(Long id, Long menuId, TableBillStatus status);

    @Query("""
            select b from TableBill b
            where b.menuId in :menuIds
              and b.status = :status
              and b.closedAt is not null
              and (:from is null or b.closedAt >= :from)
              and (:to is null or b.closedAt <= :to)
            """)
    List<TableBill> findClosedBillsForMenus(
            @Param("menuIds") Collection<Long> menuIds,
            @Param("status") TableBillStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
