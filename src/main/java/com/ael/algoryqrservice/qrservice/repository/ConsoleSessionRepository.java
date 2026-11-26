package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsoleSessionRepository extends JpaRepository<ConsoleSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from ConsoleSession s
            where s.id = :id and s.merchantId = :merchantId and s.deleted = false
            """)
    Optional<ConsoleSession> lockOwned(@Param("id") Long id, @Param("merchantId") Long merchantId);

    boolean existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(Long consoleId);

    List<ConsoleSession> findByBranchIdAndMerchantIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtAscIdAsc(
            Long branchId,
            Long merchantId
    );

    Page<ConsoleSession> findByBranchIdAndMerchantIdAndEndedAtIsNotNullAndDeletedFalseOrderByEndedAtDescIdDesc(
            Long branchId,
            Long merchantId,
            Pageable pageable
    );

    /** Finished sessions of the merchant (every branch) that ended in the period. */
    @Query("""
            select s from ConsoleSession s
            where s.merchantId = :merchantId
              and s.deleted = false
              and s.endedAt is not null
              and s.endedAt between :from and :to
              and s.cancelledAt is null
            """)
    List<ConsoleSession> findFinishedForMerchant(
            @Param("merchantId") Long merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /** Finished sessions of one branch that ended in the period. */
    @Query("""
            select s from ConsoleSession s
            where s.merchantId = :merchantId
              and s.branchId = :branchId
              and s.deleted = false
              and s.endedAt is not null
              and s.endedAt between :from and :to
              and s.cancelledAt is null
            """)
    List<ConsoleSession> findFinishedForBranch(
            @Param("merchantId") Long merchantId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select s from ConsoleSession s
            where s.branchId = :branchId and s.deleted = false and s.endedAt is not null
              and s.endedAt between :from and :to
              and s.cancelledAt is null
            """)
    List<ConsoleSession> findClosedAtBranch(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    Optional<ConsoleSession> findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalse(Long consoleId);

    /** Every open session, for the overdue job. */
    List<ConsoleSession> findByEndedAtIsNullAndDeletedFalse();

    /** Open sessions the overdue job flagged, for the owner's warning. */
    List<ConsoleSession> findByMerchantIdAndEndedAtIsNullAndOverdueAtIsNotNullAndDeletedFalseOrderByOverdueAtAscIdAsc(Long merchantId);
}
