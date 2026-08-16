package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableBillItemRepository extends JpaRepository<TableBillItem, Long> {

    Optional<TableBillItem> findByIdAndBillId(Long id, Long billId);
}
