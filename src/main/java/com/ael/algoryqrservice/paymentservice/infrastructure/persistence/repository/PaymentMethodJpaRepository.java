package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentMethodEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {

    Page<PaymentMethodEntity> findByAccountIdAndDeletedAtIsNull(String accountId, Pageable pageable);

    Optional<PaymentMethodEntity> findByIdAndAccountIdAndDeletedAtIsNull(Long id, String accountId);

    Optional<PaymentMethodEntity> findFirstByAccountIdAndDefaultMethodTrueAndDeletedAtIsNull(String accountId);

    Optional<PaymentMethodEntity> findFirstByAccountIdAndDeletedAtIsNullOrderById(String accountId);

    List<PaymentMethodEntity> findByAccountIdAndDeletedAtIsNullOrderByDefaultMethodDescIdAsc(String accountId);

    boolean existsByAccountIdAndDeletedAtIsNull(String accountId);

    @Modifying
    @Query("""
            update PaymentMethodEntity paymentMethod
            set paymentMethod.defaultMethod = false
            where paymentMethod.accountId = :accountId
              and paymentMethod.deletedAt is null
            """)
    void clearDefault(String accountId);
}
