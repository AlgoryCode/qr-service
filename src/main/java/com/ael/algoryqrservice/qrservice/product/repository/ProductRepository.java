package com.ael.algoryqrservice.qrservice.product.repository;

import com.ael.algoryqrservice.qrservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    List<Product> findByCodeIn(List<String> codes);

    List<Product> findByActiveTrueOrderByCodeAsc();

    List<Product> findByActiveTrueAndAddonPurchasableTrueOrderByNameAsc();

    List<Product> findByFeatureCode(String featureCode);
}
