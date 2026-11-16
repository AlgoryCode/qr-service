package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public List<InvoiceResponse> mine(@RequestParam(defaultValue = "50") int limit) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return invoiceService.listForMerchant(merchantId, Math.max(1, Math.min(limit, 200))).stream()
                .map(InvoiceResponse::of)
                .toList();
    }

    public record InvoiceResponse(
            Long id,
            Long purchaseId,
            String kind,
            String status,
            String description,
            String currency,
            BigDecimal subtotal,
            BigDecimal vatAmount,
            BigDecimal total,
            String invoiceNumber,
            String documentUrl,
            LocalDateTime issuedAt,
            LocalDateTime createdAt
    ) {
        static InvoiceResponse of(BillingInvoice invoice) {
            return new InvoiceResponse(invoice.getId(), invoice.getPurchaseId(), invoice.getKind().name(),
                    invoice.getStatus().name(), invoice.getDescription(), invoice.getCurrency(),
                    invoice.getSubtotal(), invoice.getVatAmount(), invoice.getTotal(), invoice.getInvoiceNumber(),
                    invoice.getDocumentUrl(), invoice.getIssuedAt(), invoice.getCreatedAt());
        }
    }
}
