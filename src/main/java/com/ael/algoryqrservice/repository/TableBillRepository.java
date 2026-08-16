package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
