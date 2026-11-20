package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTournamentPlayer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTournamentPlayerRepository extends JpaRepository<DeviceTournamentPlayer, Long> {

    List<DeviceTournamentPlayer> findByTournamentIdAndDeletedFalseOrderByIdAsc(Long tournamentId);

    List<DeviceTournamentPlayer> findByTournamentIdInAndDeletedFalse(Collection<Long> tournamentIds);

    Optional<DeviceTournamentPlayer> findByIdAndTournamentIdAndDeletedFalse(Long id, Long tournamentId);

    @Query("""
            select p from DeviceTournamentPlayer p, DeviceTournament t
            where p.tournamentId = t.id and t.branchId = :branchId and p.deleted = false and p.paid = true
              and p.paidAt between :from and :to
            """)
    List<DeviceTournamentPlayer> findPaidAtBranch(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
