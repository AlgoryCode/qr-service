package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MerchantStaffSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantStaffSessionRepository extends JpaRepository<MerchantStaffSession, UUID> {

    Optional<MerchantStaffSession> findByIdAndStaffId(UUID id, Long staffId);
}
