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

    @org.springframework.data.jpa.repository.Query("""
            select t from DeviceCustomerTxn t
            where t.branchId = :branchId and t.deleted = false and t.createdAt between :from and :to
            """)
    java.util.List<DeviceCustomerTxn> findAtBranch(
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to
    );
}
