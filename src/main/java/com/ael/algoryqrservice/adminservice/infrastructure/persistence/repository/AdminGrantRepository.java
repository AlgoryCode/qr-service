package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminGrantRepository extends JpaRepository<AdminGrant, Long> {
    List<AdminGrant> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
