package com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.customerservice.infrastructure.persistence.entity.Customer;
import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Customer> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
