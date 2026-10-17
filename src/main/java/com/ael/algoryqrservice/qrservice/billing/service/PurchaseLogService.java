package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.model.PurchaseLog;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseLogResponse;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.qrservice.repository.PurchaseLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseLogService {

    private final PurchaseLogRepository purchaseLogRepository;

    @Transactional
    public void log(Long purchaseId, Long merchantId, PurchaseLogAction action, String message) {
        purchaseLogRepository.save(PurchaseLog.builder()
                .purchaseId(purchaseId)
                .merchantId(merchantId)
                .action(action)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<PurchaseLogResponse> getUserLogs(Long merchantId) {
        return purchaseLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseLogResponse> getPurchaseLogs(Long purchaseId) {
        return purchaseLogRepository.findByPurchaseIdOrderByCreatedAtDesc(purchaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PurchaseLogResponse toResponse(PurchaseLog log) {
        return PurchaseLogResponse.builder()
                .id(log.getId())
                .purchaseId(log.getPurchaseId())
                .merchantId(log.getMerchantId())
                .action(log.getAction())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
