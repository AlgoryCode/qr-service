package com.ael.algoryqrservice.print.repository;

import com.ael.algoryqrservice.print.model.PrintJob;
import com.ael.algoryqrservice.print.model.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    Optional<PrintJob> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT j FROM PrintJob j
            WHERE j.branchId = :branchId
              AND j.status = com.ael.algoryqrservice.print.model.PrintJobStatus.PENDING
            ORDER BY j.createdAt ASC
            """)
    List<PrintJob> findPendingForBranch(@Param("branchId") Long branchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PrintJob j
            SET j.status = com.ael.algoryqrservice.print.model.PrintJobStatus.PENDING,
                j.claimedByDeviceId = NULL,
                j.claimedAt = NULL,
                j.updatedAt = :now
            WHERE j.status = com.ael.algoryqrservice.print.model.PrintJobStatus.CLAIMED
              AND j.claimedAt < :staleBefore
            """)
    int releaseStaleClaims(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PrintJob j
            SET j.status = com.ael.algoryqrservice.print.model.PrintJobStatus.CLAIMED,
                j.claimedByDeviceId = :deviceId,
                j.claimedAt = :now,
                j.attempts = j.attempts + 1,
                j.updatedAt = :now
            WHERE j.id = :jobId
              AND j.status = com.ael.algoryqrservice.print.model.PrintJobStatus.PENDING
            """)
    int claimJob(@Param("jobId") Long jobId, @Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);

    Optional<PrintJob> findByIdAndClaimedByDeviceId(Long id, Long claimedByDeviceId);

    Optional<PrintJob> findByIdAndMerchantId(Long id, Long merchantId);

    Optional<PrintJob> findByIdAndBranchId(Long id, Long branchId);

    List<PrintJob> findByMerchantIdAndBranchIdAndStatusOrderByCreatedAtDesc(
            Long merchantId,
            Long branchId,
            PrintJobStatus status
    );

    List<PrintJob> findByBranchIdOrderByCreatedAtDesc(Long branchId);
}
