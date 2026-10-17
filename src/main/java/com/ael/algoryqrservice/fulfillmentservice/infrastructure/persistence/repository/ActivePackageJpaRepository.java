package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.ActivePackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivePackageJpaRepository extends JpaRepository<ActivePackageEntity, Long> {

    Optional<ActivePackageEntity> findByMerchantId(Long merchantId);
}
