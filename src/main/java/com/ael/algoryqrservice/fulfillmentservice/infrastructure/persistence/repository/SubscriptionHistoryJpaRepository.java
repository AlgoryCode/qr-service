package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.SubscriptionHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryJpaRepository extends JpaRepository<SubscriptionHistoryEntity, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<SubscriptionHistoryEntity> findByMerchantIdOrderByCreatedAtDescIdDesc(Long merchantId, Pageable pageable);
}
