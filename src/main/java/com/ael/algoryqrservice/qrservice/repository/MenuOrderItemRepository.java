package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.MenuOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuOrderItemRepository extends JpaRepository<MenuOrderItem, Long> {

    List<MenuOrderItem> findByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}
