package com.ael.algoryqrservice.stage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StageTrialAssignmentRepository extends JpaRepository<StageTrialAssignment, Long> {

    boolean existsByUserId(Long userId);

    @Query("select assignment.userId from StageTrialAssignment assignment where assignment.userId <> :templateUserId")
    List<Long> findUserIdsExcept(@Param("templateUserId") Long templateUserId);
}
