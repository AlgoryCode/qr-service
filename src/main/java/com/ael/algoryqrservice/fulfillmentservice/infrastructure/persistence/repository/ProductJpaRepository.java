package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    List<Product> findByIdIn(Collection<Long> ids);
}
