package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.OrderFlowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderFlowRequestRepository extends JpaRepository<OrderFlowRequest, Long> {

    List<OrderFlowRequest> findByMerchantIdAndDeletedFalseOrderByIdDesc(Long merchantId);
}
