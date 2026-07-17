package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.BillingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingAddressRepository extends JpaRepository<BillingAddress, Long> {
    List<BillingAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);
    Optional<BillingAddress> findByIdAndUserId(Long id, Long userId);
    Optional<BillingAddress> findByUserIdAndDefaultAddressTrue(Long userId);
}
