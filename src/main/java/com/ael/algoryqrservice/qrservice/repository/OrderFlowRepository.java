package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.OrderFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderFlowRepository extends JpaRepository<OrderFlow, Long> {

    List<OrderFlow> findByMerchantIdAndDeletedFalseOrderByIdAsc(Long merchantId);

    Optional<OrderFlow> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    Optional<OrderFlow> findByIdAndDeletedFalse(Long id);
}
