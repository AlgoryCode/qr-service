package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByUserIdOrderByPurchasedAtDesc(Long userId);

    List<Purchase> findByStatusAndExpiresAtBefore(PurchaseStatus status, LocalDateTime expiresAt);

    List<Purchase> findByStatusAndPurchasedAtBefore(PurchaseStatus status, LocalDateTime purchasedAt);

    List<Purchase> findByUserIdAndStatus(Long userId, PurchaseStatus status);

    boolean existsByUserIdAndStatus(Long userId, PurchaseStatus status);

    boolean existsByUserIdAndPurchaseType(Long userId, PurchaseType purchaseType);

    Optional<Purchase> findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(Long userId, PurchaseType purchaseType);

    List<Purchase> findByPurchaseTypeAndStatusAndExpiresAtGreaterThanEqualAndExpiresAtLessThan(
            PurchaseType purchaseType,
            PurchaseStatus status,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from Purchase purchase where purchase.id = :purchaseId")
    Optional<Purchase> findByIdForUpdate(@Param("purchaseId") Long purchaseId);

    @Query("""
            select distinct p.userId
            from Purchase p
            where p.status = :status
              and p.purchaseType <> com.ael.algoryqrservice.model.enums.PurchaseType.FREE
            """)
    List<Long> findDistinctUserIdsWithExpiredPaidPurchases(@Param("status") PurchaseStatus status);
}
