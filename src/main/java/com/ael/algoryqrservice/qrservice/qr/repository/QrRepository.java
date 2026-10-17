package com.ael.algoryqrservice.qrservice.qr.repository;

import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QrRepository extends JpaRepository<Qr,Long> {
    List<Qr> findByMerchantIdAndDeletedFalseOrderByCreatedAtDesc(Long merchantId);

    long countByUserIdAndDeletedFalse(Long merchantId);

    long countByUserIdAndDeletedFalseAndQrType_TypeNameNotIgnoreCase(Long merchantId, String typeName);

    long countByUserIdAndPurchaseIdAndDeletedFalse(Long merchantId, Long purchaseId);
}
