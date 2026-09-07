package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrialLogRepository extends JpaRepository<TrialLog, Long> {

    Optional<TrialLog> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<TrialLog> findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
            TrialLogStatus status,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    );
}
