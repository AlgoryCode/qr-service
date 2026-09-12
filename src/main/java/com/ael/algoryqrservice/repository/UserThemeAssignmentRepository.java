package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserThemeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserThemeAssignmentRepository extends JpaRepository<UserThemeAssignment, Long> {

    List<UserThemeAssignment> findByUserId(Long userId);

    Optional<UserThemeAssignment> findByUserIdAndThemeId(Long userId, Long themeId);

    boolean existsByUserIdAndThemeId(Long userId, Long themeId);

    void deleteByUserIdAndThemeId(Long userId, Long themeId);

    void deleteByUserId(Long userId);

    long countByUserId(Long userId);

    List<UserThemeAssignment> findByUserIdAndThemeIdIn(Long userId, Collection<Long> themeIds);
}
