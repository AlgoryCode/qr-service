package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.AdminPaymentDtos;
import com.ael.algoryqrservice.service.AdminPaymentService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping
    public ResponseEntity<AdminPaymentDtos.PaymentPageResponse> listPayments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String paymentStyle,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) Boolean verificationOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminPaymentService.listPayments(
                q,
                status,
                paymentType,
                paymentStyle,
                accountId,
                createdFrom,
                createdTo,
                verificationOnly,
                page,
                size
        ));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<AdminPaymentDtos.PaymentDetailResponse> getPayment(
            @PathVariable String conversationId
    ) {
        return ResponseEntity.ok(adminPaymentService.getPayment(conversationId));
    }

    @PostMapping("/{conversationId}/refund")
    public ResponseEntity<AdminPaymentDtos.RefundResponse> refundPayment(
            @PathVariable String conversationId,
            @Valid @RequestBody(required = false) AdminPaymentDtos.RefundRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(adminPaymentService.refundPayment(
                conversationId,
                request == null ? null : request.getAmount(),
                ClientInfo.from(httpRequest).ipAddress()
        ));
    }
}
