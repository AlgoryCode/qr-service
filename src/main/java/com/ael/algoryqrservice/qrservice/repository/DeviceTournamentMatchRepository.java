package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTournamentMatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTournamentMatchRepository extends JpaRepository<DeviceTournamentMatch, Long> {

    List<DeviceTournamentMatch> findByTournamentIdAndDeletedFalseOrderByRoundAscPositionAsc(Long tournamentId);

    Optional<DeviceTournamentMatch> findByIdAndTournamentIdAndDeletedFalse(Long id, Long tournamentId);

    Optional<DeviceTournamentMatch> findByTournamentIdAndRoundAndPositionAndDeletedFalse(Long tournamentId, int round, int position);
}
