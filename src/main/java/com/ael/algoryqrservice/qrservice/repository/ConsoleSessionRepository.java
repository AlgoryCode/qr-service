package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
