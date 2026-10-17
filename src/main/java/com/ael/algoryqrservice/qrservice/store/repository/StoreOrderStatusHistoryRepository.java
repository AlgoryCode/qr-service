package com.ael.algoryqrservice.qrservice.store.repository;

import com.ael.algoryqrservice.qrservice.store.model.StoreOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreOrderStatusHistoryRepository extends JpaRepository<StoreOrderStatusHistory, Long> {

    List<StoreOrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
