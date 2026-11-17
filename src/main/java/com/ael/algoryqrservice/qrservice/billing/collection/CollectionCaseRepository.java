package com.ael.algoryqrservice.qrservice.billing.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionCaseRepository extends JpaRepository<CollectionCase, Long> {

    List<CollectionCase> findByClosedAtIsNull();

    Optional<CollectionCase> findFirstByMerchantIdAndClosedAtIsNull(Long merchantId);
}
