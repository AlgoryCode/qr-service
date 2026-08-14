package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Qr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QrRepository extends JpaRepository<Qr,Long> {
    List<Qr> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    long countByUserIdAndPurchaseIdAndDeletedFalse(Long userId, Long purchaseId);
}
