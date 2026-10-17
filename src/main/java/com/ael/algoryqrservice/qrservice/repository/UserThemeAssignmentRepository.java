package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.UserThemeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserThemeAssignmentRepository extends JpaRepository<UserThemeAssignment, Long> {

    List<UserThemeAssignment> findByMerchantId(Long merchantId);

    Optional<UserThemeAssignment> findByMerchantIdAndThemeId(Long merchantId, Long themeId);

    boolean existsByMerchantIdAndThemeId(Long merchantId, Long themeId);

    void deleteByUserIdAndThemeId(Long merchantId, Long themeId);

    void deleteByUserId(Long merchantId);

    long countByUserId(Long merchantId);

    List<UserThemeAssignment> findByMerchantIdAndThemeIdIn(Long merchantId, Collection<Long> themeIds);
}
