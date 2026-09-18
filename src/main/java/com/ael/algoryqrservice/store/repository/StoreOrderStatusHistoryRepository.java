package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.StoreOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreOrderStatusHistoryRepository extends JpaRepository<StoreOrderStatusHistory, Long> {

    List<StoreOrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
