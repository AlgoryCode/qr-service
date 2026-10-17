package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentRepositoryPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.PaymentJpaRepository;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.specification.PaymentSearchSpecificationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentPersistenceMapper paymentPersistenceMapper;
    private final PaymentSearchSpecificationFactory paymentSearchSpecificationFactory;

    @Override
    public PaymentTransaction save(PaymentTransaction paymentTransaction) {
        PaymentEntity entity = paymentPersistenceMapper.toEntity(paymentTransaction);
        PaymentEntity savedEntity = paymentJpaRepository.save(entity);
        return paymentPersistenceMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentTransaction> findByConversationId(String conversationId) {
        return paymentJpaRepository.findByConversationId(conversationId)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByGatewayToken(String gatewayToken) {
        return paymentJpaRepository.findByGatewayToken(gatewayToken)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByConversationId(String conversationId) {
        return paymentJpaRepository.existsByConversationId(conversationId);
    }

    @Override
    public Page<PaymentTransaction> search(PaymentSearchQuery query, Pageable pageable) {
        return paymentJpaRepository
                .findAll(paymentSearchSpecificationFactory.create(query), pageable)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public List<PaymentTransaction> findStaleByStatus(PaymentStatus status, LocalDateTime olderThan, int limit) {        return paymentJpaRepository
                .findByStatusAndCreatedAtLessThanOrderByCreatedAt(status, olderThan, PageRequest.of(0, limit))
                .stream()
                .map(paymentPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<PaymentTransaction> findVerificationChargesPendingRefund(
            PaymentStatus status,
            LocalDateTime updatedBefore,
            LocalDateTime createdAfter,
            int limit
    ) {
        return paymentJpaRepository
                .findVerificationChargesPendingRefund(
                        status,
                        updatedBefore,
                        createdAfter,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(paymentPersistenceMapper::toDomain)
                .toList();
    }
}
