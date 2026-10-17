package com.ael.algoryqrservice.paymentservice.domain.port;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {

    PaymentTransaction save(PaymentTransaction paymentTransaction);

    Optional<PaymentTransaction> findByConversationId(String conversationId);

    Optional<PaymentTransaction> findByGatewayToken(String gatewayToken);

    boolean existsByConversationId(String conversationId);

    Page<PaymentTransaction> search(PaymentSearchQuery query, Pageable pageable);

    List<PaymentTransaction> findStaleByStatus(PaymentStatus status, LocalDateTime olderThan, int limit);

    List<PaymentTransaction> findVerificationChargesPendingRefund(
            PaymentStatus status,
            LocalDateTime updatedBefore,
            LocalDateTime createdAfter,
            int limit
    );
}
