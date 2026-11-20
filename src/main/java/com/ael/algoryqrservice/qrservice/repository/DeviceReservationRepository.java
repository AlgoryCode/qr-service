package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceReservationStatus;
import com.ael.algoryqrservice.qrservice.model.DeviceReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceReservationRepository extends JpaRepository<DeviceReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeviceReservation r where r.id = :id and r.merchantId = :merchantId and r.deleted = false")
    Optional<DeviceReservation> lockOwned(@Param("id") Long id, @Param("merchantId") Long merchantId);

    /** Reservations of the branch that overlap [from, to). */
    @Query("""
            select r from DeviceReservation r
            where r.branchId = :branchId and r.merchantId = :merchantId and r.deleted = false
              and r.startsAt < :to and r.endsAt > :from
            order by r.startsAt asc, r.id asc
            """)
    List<DeviceReservation> findOverlappingForBranch(
            @Param("branchId") Long branchId,
            @Param("merchantId") Long merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /** Pass {@code -1L} as {@code excludeId} when nothing is excluded. */
    @Query("""
            select count(r) > 0 from DeviceReservation r
            where r.consoleId = :consoleId and r.deleted = false and r.status = :status
              and r.startsAt < :to and r.endsAt > :from
              and r.id <> :excludeId
            """)
    boolean existsOverlap(
            @Param("consoleId") Long consoleId,
            @Param("status") DeviceReservationStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("excludeId") Long excludeId
    );
}
