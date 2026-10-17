package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {

    Optional<PaymentEntity> findByConversationId(String conversationId);

    Optional<PaymentEntity> findByGatewayToken(String gatewayToken);

    boolean existsByConversationId(String conversationId);

    List<PaymentEntity> findByStatusAndCreatedAtLessThanOrderByCreatedAt(
            PaymentStatus status,
            LocalDateTime createdAt,
            Pageable pageable
    );

    @Query("""
            select payment from PaymentEntity payment
            where payment.verificationOnly = true
              and payment.status = :status
              and payment.refundedAmount < payment.paidPrice
              and payment.updatedAt <= :updatedBefore
              and payment.createdAt >= :createdAfter
            order by payment.updatedAt asc
            """)
    List<PaymentEntity> findVerificationChargesPendingRefund(
            @Param("status") PaymentStatus status,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            @Param("createdAfter") LocalDateTime createdAfter,
            Pageable pageable
    );
}
