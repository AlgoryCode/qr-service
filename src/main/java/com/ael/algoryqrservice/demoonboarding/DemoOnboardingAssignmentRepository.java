package com.ael.algoryqrservice.demoonboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoOnboardingAssignmentRepository extends JpaRepository<DemoOnboardingAssignment, Long> {

    boolean existsByUserId(Long userId);
}
