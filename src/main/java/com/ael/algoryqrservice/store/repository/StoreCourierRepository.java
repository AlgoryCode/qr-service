package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.StoreCourier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreCourierRepository extends JpaRepository<StoreCourier, Long> {

    List<StoreCourier> findByMerchantIdAndDeletedFalseOrderByFullNameAsc(Long merchantId);

    Optional<StoreCourier> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);
}
