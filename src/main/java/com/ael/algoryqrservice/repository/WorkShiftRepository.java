package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.WorkShift;
import com.ael.algoryqrservice.model.enums.WorkShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    Optional<WorkShift> findFirstByBranchIdAndStatusOrderByOpenedAtDesc(Long branchId, WorkShiftStatus status);

    List<WorkShift> findByBranchIdAndOpenedAtBetweenOrderByOpenedAtDesc(
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<WorkShift> findByIdAndBranchId(Long id, Long branchId);
}
