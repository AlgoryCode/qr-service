package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdAndDeletedFalseOrderByDefaultAddressDescIdDesc(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(Long id, Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CustomerAddress address
            set address.defaultAddress = false
            where address.customerId = :customerId
              and address.deleted = false
            """)
    void clearDefaultForCustomer(@Param("customerId") Long customerId);
}
