package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTournament;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTournamentRepository extends JpaRepository<DeviceTournament, Long> {

    List<DeviceTournament> findByBranchIdAndMerchantIdAndDeletedFalseOrderByStartsAtDescIdDesc(Long branchId, Long merchantId);

    Optional<DeviceTournament> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeviceTournament t where t.id = :id and t.merchantId = :merchantId and t.deleted = false")
    Optional<DeviceTournament> lockOwned(@Param("id") Long id, @Param("merchantId") Long merchantId);
}
