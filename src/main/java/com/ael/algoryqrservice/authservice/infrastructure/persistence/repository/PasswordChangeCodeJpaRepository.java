package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.PasswordChangeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordChangeCodeJpaRepository extends JpaRepository<PasswordChangeCodeEntity, Long> {

    List<PasswordChangeCodeEntity> findByMerchantIdAndStatusAndRevokedFalseOrderByCreatedAtDesc(
            Long merchantId,
            String status
    );
}
