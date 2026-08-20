package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableBillRepository extends JpaRepository<TableBill, Long>, JpaSpecificationExecutor<TableBill> {

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByMenuIdAndTableIdAndStatus(Long menuId, Long tableId, TableBillStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByIdAndMenuId(Long id, Long menuId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT b FROM TableBill b WHERE b.id = :id")
    Optional<TableBill> findWithItemsById(@Param("id") Long id);

    List<TableBill> findByMenuIdAndStatus(Long menuId, TableBillStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<TableBill> findByIdAndMenuIdAndStatus(Long id, Long menuId, TableBillStatus status);

    @EntityGraph(attributePaths = "items")
    List<TableBill> findByMenuIdAndStatusAndClosedAtBetween(
            Long menuId,
            TableBillStatus status,
            LocalDateTime start,
            LocalDateTime end
    );
}
