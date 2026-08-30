package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.AdminPaymentDtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AdminPaymentService {

    AdminPaymentDtos.PaymentPageResponse listPayments(
            String query,
            String status,
            String paymentType,
            String paymentStyle,
            String accountId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Boolean verificationOnly,
            int page,
            int size
    );

    AdminPaymentDtos.PaymentDetailResponse getPayment(String conversationId);

    AdminPaymentDtos.RefundResponse refundPayment(
            String conversationId,
            BigDecimal amount,
            String clientIp
    );
}
