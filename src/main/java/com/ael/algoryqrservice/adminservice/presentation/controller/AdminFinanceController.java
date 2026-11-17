package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AdminExportService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.application.DunningService;
import com.ael.algoryqrservice.adminservice.application.RevenueService;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.BillingReminder;
import com.ael.algoryqrservice.qrservice.billing.invoice.InvoiceService;
import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import com.ael.algoryqrservice.qrservice.repository.BillingInvoiceRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Revenue, invoices, payment reminders and CSV exports (finance; reminders also support). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceController {

    private final RevenueService revenueService;
    private final InvoiceService invoiceService;
    private final BillingInvoiceRepository invoiceRepository;
    private final DunningService dunningService;
    private final AdminExportService exportService;
    private final DashboardSecurityUtils securityUtils;

    @GetMapping("/api/v1/admin/revenue")
    public RevenueService.Summary revenue(@RequestParam(defaultValue = "12") int months) {
        return revenueService.summary(months);
    }

    public record InvoiceRow(Long id, Long merchantId, Long purchaseId, String kind, String status, String description,
                             BigDecimal total, BigDecimal vatAmount, String currency, String buyerName, String buyerEmail,
                             String buyerTaxId, String invoiceNumber, String documentUrl, int attempts, String lastError,
                             LocalDateTime issuedAt, LocalDateTime createdAt) {
    }

    public record InvoicePage(List<InvoiceRow> items, long total, int page, int size, boolean hasNext,
                              long pending, long failed) {
    }

    @GetMapping("/api/v1/admin/invoices")
    public InvoicePage invoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Specification<BillingInvoice> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                all.add(cb.equal(root.get("status"), BillingInvoice.Status.valueOf(status.toUpperCase(Locale.ROOT))));
            }
            if (kind != null && !kind.isBlank()) {
                all.add(cb.equal(root.get("kind"), BillingInvoice.Kind.valueOf(kind.toUpperCase(Locale.ROOT))));
            }
            if (merchantId != null) {
                all.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                all.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("buyerName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("buyerEmail"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("invoiceNumber"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("buyerTaxId"), "")), like)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<BillingInvoice> result = invoiceRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        long pending = invoiceRepository.count((root, query, cb) -> cb.equal(root.get("status"), BillingInvoice.Status.PENDING));
        long failed = invoiceRepository.count((root, query, cb) -> cb.equal(root.get("status"), BillingInvoice.Status.FAILED));
        return new InvoicePage(result.getContent().stream().map(AdminFinanceController::row).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.hasNext(), pending, failed);
    }

    @PostMapping("/api/v1/admin/invoices/{id}/retry")
    public InvoiceRow retry(@PathVariable Long id) {
        return row(invoiceService.retry(id));
    }

    public record ReminderRow(Long id, String kind, String email, String sentBy, LocalDateTime sentAt, LocalDateTime periodEnd) {
    }

    @GetMapping("/api/v1/admin/billing/reminders/{merchantId}")
    public List<ReminderRow> reminders(@PathVariable Long merchantId) {
        return dunningService.history(merchantId).stream().map(AdminFinanceController::reminder).toList();
    }

    @PostMapping("/api/v1/admin/billing/reminders/{merchantId}")
    public ReminderRow remind(@PathVariable Long merchantId) {
        var me = securityUtils.getCurrentDashboardUser();
        return reminder(dunningService.remindNow(merchantId, me.getUsername() != null ? me.getUsername() : me.getEmail()));
    }

    @GetMapping("/api/v1/admin/exports/{kind}")
    public ResponseEntity<byte[]> export(
            @PathVariable String kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String csv = exportService.export(kind, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay());
        String file = "algory-" + kind + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static InvoiceRow row(BillingInvoice i) {
        return new InvoiceRow(i.getId(), i.getMerchantId(), i.getPurchaseId(), i.getKind().name(), i.getStatus().name(),
                i.getDescription(), i.getTotal(), i.getVatAmount(), i.getCurrency(), i.getBuyerName(), i.getBuyerEmail(),
                i.getBuyerTaxId(), i.getInvoiceNumber(), i.getDocumentUrl(), i.getAttempts(), i.getLastError(),
                i.getIssuedAt(), i.getCreatedAt());
    }

    private static ReminderRow reminder(BillingReminder r) {
        return new ReminderRow(r.getId(), r.getKind().name(), r.getEmail(), r.getSentBy(), r.getSentAt(), r.getPeriodEnd());
    }
}
