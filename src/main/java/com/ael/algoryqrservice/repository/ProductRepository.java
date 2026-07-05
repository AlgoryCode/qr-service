package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.ProductCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(ProductCode code);

    boolean existsByCode(ProductCode code);
}
