package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.PurchaseLog;
import com.ael.algoryqrservice.model.dto.PurchaseLogResponse;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.repository.PurchaseLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseLogService {

    private final PurchaseLogRepository purchaseLogRepository;

    @Transactional
    public void log(Long purchaseId, Long userId, PurchaseLogAction action, String message) {
        purchaseLogRepository.save(PurchaseLog.builder()
                .purchaseId(purchaseId)
                .userId(userId)
                .action(action)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<PurchaseLogResponse> getUserLogs(Long userId) {
        return purchaseLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
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
                .userId(log.getUserId())
                .action(log.getAction())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
