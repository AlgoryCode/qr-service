package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCustomerTxnRepository extends JpaRepository<DeviceCustomerTxn, Long> {

    Page<DeviceCustomerTxn> findByCustomerIdAndMerchantIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long customerId,
            Long merchantId,
            Pageable pageable
    );
}
