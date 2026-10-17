package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.SubscriptionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String> {

    Optional<SubscriptionEntity> findByIdAndAccountId(String id, String accountId);

    Optional<SubscriptionEntity> findByInitialConversationId(String conversationId);

    Optional<SubscriptionEntity> findFirstBySourceReferenceIdAndAccountIdAndStatusInOrderByCreatedAtDesc(
            String sourceReferenceId,
            String accountId,
            List<String> statuses
    );

    List<SubscriptionEntity> findByStatusInAndNextChargeAtLessThanEqualOrderByNextChargeAt(
            List<String> statuses,
            LocalDateTime now,
            Pageable pageable
    );
}
