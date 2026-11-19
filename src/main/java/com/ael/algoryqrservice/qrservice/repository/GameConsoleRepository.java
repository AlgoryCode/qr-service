package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.GameConsole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameConsoleRepository extends JpaRepository<GameConsole, Long> {

    Optional<GameConsole> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    List<GameConsole> findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(Long branchId, Long merchantId);

    List<GameConsole> findByIdIn(Collection<Long> ids);

    /** Serialises starts on one console so two clicks cannot open two sessions. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GameConsole c where c.id = :id and c.merchantId = :merchantId and c.deleted = false")
    Optional<GameConsole> lockOwned(@Param("id") Long id, @Param("merchantId") Long merchantId);
}
