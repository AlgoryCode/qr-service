package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceMembership;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceMembershipRepository extends JpaRepository<DeviceMembership, Long> {

    List<DeviceMembership> findByCustomerIdAndMerchantIdAndDeletedFalseOrderByEndsAtDescIdDesc(Long customerId, Long merchantId);

    Optional<DeviceMembership> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    /** Memberships that can still be used now, the one ending first first. */
    @Query("""
            select m from DeviceMembership m
            where m.customerId = :customerId and m.merchantId = :merchantId and m.deleted = false
              and m.cancelled = false and m.minutesLeft > 0 and m.startsAt <= :now and m.endsAt > :now
            order by m.endsAt asc, m.id asc
            """)
    List<DeviceMembership> findUsable(
            @Param("customerId") Long customerId,
            @Param("merchantId") Long merchantId,
            @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DeviceMembership m where m.id = :id")
    Optional<DeviceMembership> lockById(@Param("id") Long id);

    @Query("""
            select m from DeviceMembership m
            where m.branchId = :branchId and m.deleted = false and m.createdAt between :from and :to
            """)
    List<DeviceMembership> findSoldAtBranch(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
