package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByUserIdAndDeletedFalseOrderByIdDesc(Long userId);

    Optional<Branch> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    long countByUserIdAndGrandfatheredTrueAndDeletedFalse(Long userId);

    List<Branch> findByUserIdAndDeletedFalse(Long userId);
}
