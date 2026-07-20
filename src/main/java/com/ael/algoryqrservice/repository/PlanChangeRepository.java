package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlanChange;
import com.ael.algoryqrservice.model.enums.PlanChangeStatus;
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
public interface PlanChangeRepository extends JpaRepository<PlanChange, Long> {

    List<PlanChange> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PlanChange> findByUserIdAndStatus(Long userId, PlanChangeStatus status);

    Optional<PlanChange> findByResultingPurchaseId(Long resultingPurchaseId);

    List<PlanChange> findByStatusAndEffectiveAtLessThanEqual(PlanChangeStatus status, LocalDateTime effectiveAt);

    boolean existsByUserIdAndStatus(Long userId, PlanChangeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pc from PlanChange pc where pc.id = :id")
    Optional<PlanChange> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pc from PlanChange pc where pc.resultingPurchaseId = :purchaseId")
    Optional<PlanChange> findByResultingPurchaseIdForUpdate(@Param("purchaseId") Long purchaseId);
}
