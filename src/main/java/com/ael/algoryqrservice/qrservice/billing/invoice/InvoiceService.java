package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import com.ael.algoryqrservice.qrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.repository.BillingInvoiceRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps one invoice row per collected payment and per refund, written in the same transaction as
 * the payment event, so the ledger never misses a charge. Issuing happens later in
 * {@link InvoiceIssueJob}; prices are VAT-inclusive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    static final int MAX_ATTEMPTS = 8;

    private final BillingInvoiceRepository repository;
    private final EInvoiceProvider provider;

    @Value("${billing.invoice.vat-rate:20}")
    private BigDecimal vatRate = BigDecimal.valueOf(20);

    @Transactional
    public void recordSale(Purchase purchase, PaymentCompletedEventDto event) {
        record(purchase, event, BillingInvoice.Kind.SALE);
    }

    @Transactional
    public void recordRefund(Purchase purchase, PaymentCompletedEventDto event) {
        record(purchase, event, BillingInvoice.Kind.REFUND);
    }

    private void record(Purchase purchase, PaymentCompletedEventDto event, BillingInvoice.Kind kind) {
        if (purchase == null || event == null || event.getEventId() == null || event.getAmount() == null
                || event.getAmount().signum() <= 0) {
            return;
        }
        if (repository.existsByPaymentEventIdAndKind(event.getEventId(), kind)) {
            return;
        }
        BigDecimal total = event.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100).add(vatRate), 2, RoundingMode.HALF_UP);
        BillingSnapshot buyer = purchase.getBillingSnapshot();
        BillingInvoice invoice = BillingInvoice.builder()
                .merchantId(purchase.getMerchantId())
                .purchaseId(purchase.getId())
                .paymentEventId(event.getEventId())
                .kind(kind)
                .status(BillingInvoice.Status.PENDING)
                .description(truncate((kind == BillingInvoice.Kind.REFUND ? "İade: " : "")
                        + Objects.requireNonNullElse(purchase.getPackageName(), "Abonelik"), 255))
                .currency(Objects.requireNonNullElse(purchase.getCurrency(), "TRY"))
                .subtotal(subtotal)
                .vatRate(vatRate)
                .vatAmount(total.subtract(subtotal))
                .total(total)
                .buyerName(buyer == null ? null : truncate(buyerName(buyer), 255))
                .buyerTaxId(buyer == null ? null : firstNonBlank(buyer.getVkn(), buyer.getTckn()))
                .buyerTaxOffice(buyer == null ? null : buyer.getTaxOffice())
                .buyerAddress(buyer == null ? null : truncate(joined(buyer.getAddress(), buyer.getDistrict(),
                        buyer.getCity(), buyer.getCountry()), 1024))
                .buyerEmail(buyer == null ? null : buyer.getEmail())
                .build();
        repository.save(invoice);
        log.info("invoice_recorded merchantId={} purchaseId={} kind={} total={}",
                purchase.getMerchantId(), purchase.getId(), kind, total);
    }

    /** Sends due invoices to the provider; one failure never blocks the rest. */
    public int issuePending(int limit) {
        List<BillingInvoice> due = repository.findByStatusInAndAttemptsLessThanOrderByIdAsc(
                List.of(BillingInvoice.Status.PENDING, BillingInvoice.Status.FAILED),
                MAX_ATTEMPTS,
                PageRequest.of(0, limit));
        int issued = 0;
        for (BillingInvoice invoice : due) {
            if (issueOne(invoice)) {
                issued++;
            }
        }
        return issued;
    }

    boolean issueOne(BillingInvoice invoice) {
        invoice.setAttempts(invoice.getAttempts() + 1);
        invoice.setProvider(provider.name());
        EInvoiceProvider.Result result;
        try {
            result = provider.issue(invoice);
        } catch (RuntimeException ex) {
            result = EInvoiceProvider.Result.failed(ex.getMessage());
        }
        switch (result.outcome()) {
            case ISSUED -> {
                invoice.setStatus(BillingInvoice.Status.ISSUED);
                invoice.setProviderRef(result.providerRef());
                invoice.setInvoiceNumber(result.invoiceNumber());
                invoice.setDocumentUrl(result.documentUrl());
                invoice.setIssuedAt(AppTime.nowLocal());
                invoice.setLastError(null);
            }
            case SKIPPED -> {
                invoice.setStatus(BillingInvoice.Status.SKIPPED);
                invoice.setLastError(truncate(result.error(), 1024));
            }
            case FAILED -> {
                invoice.setStatus(BillingInvoice.Status.FAILED);
                invoice.setLastError(truncate(result.error(), 1024));
                log.warn("invoice_issue_failed invoiceId={} attempt={} error={}",
                        invoice.getId(), invoice.getAttempts(), result.error());
            }
        }
        repository.save(invoice);
        return invoice.getStatus() == BillingInvoice.Status.ISSUED;
    }

    @Transactional(readOnly = true)
    public List<BillingInvoice> listForMerchant(Long merchantId, int limit) {
        return repository.findByMerchantIdOrderByCreatedAtDescIdDesc(merchantId, PageRequest.of(0, limit));
    }

    private static String buyerName(BillingSnapshot buyer) {
        if (buyer.getLegalName() != null && !buyer.getLegalName().isBlank()) {
            return buyer.getLegalName();
        }
        return joinedWith(" ", buyer.getName(), buyer.getSurname());
    }

    private static String firstNonBlank(String... values) {
        return Stream.of(values).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }

    private static String joined(String... parts) {
        return joinedWith(", ", parts);
    }

    private static String joinedWith(String separator, String... parts) {
        String value = Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separator));
        return value.isEmpty() ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
