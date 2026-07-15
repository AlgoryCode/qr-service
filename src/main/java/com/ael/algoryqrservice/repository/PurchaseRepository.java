package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from Purchase purchase where purchase.id = :purchaseId")
    Optional<Purchase> findByIdForUpdate(@Param("purchaseId") Long purchaseId);

    @Query("""
            select distinct p.userId
            from Purchase p
            where p.packageCode = :packageCode
              and p.status = :status
            """)
    List<Long> findDistinctUserIdsByPackageCodeAndStatus(
            @Param("packageCode") PackageCode packageCode,
            @Param("status") PurchaseStatus status
    );
}
