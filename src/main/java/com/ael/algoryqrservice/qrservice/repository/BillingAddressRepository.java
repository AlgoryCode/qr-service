package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.BillingAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingAddressRepository extends JpaRepository<BillingAddress, Long> {
    List<BillingAddress> findByMerchantIdOrderByDefaultAddressDescCreatedAtDesc(Long merchantId);

    Page<BillingAddress> findByMerchantIdOrderByDefaultAddressDescCreatedAtDesc(Long merchantId, Pageable pageable);

    Optional<BillingAddress> findByIdAndMerchantId(Long id, Long merchantId);

    Optional<BillingAddress> findByMerchantIdAndDefaultAddressTrue(Long merchantId);
}
