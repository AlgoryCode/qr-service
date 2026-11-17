package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberTag;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.MemberTagRepository;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentService;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.repository.BillingInvoiceRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CSV downloads for spreadsheets: semicolon-separated with a UTF-8 BOM so Excel (Turkish
 * locale) opens them with the right columns and characters.
 */
@Service
@RequiredArgsConstructor
public class AdminExportService {

    public static final List<String> KINDS = List.of("members", "billing", "payments", "invoices", "revenue");
    static final int MAX_ROWS = 50_000;
    private static final int PAGE = 500;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;
    private final MemberTagRepository memberTagRepository;
    private final AdminBillingService billingService;
    private final PaymentService paymentService;
    private final BillingInvoiceRepository invoiceRepository;
    private final RevenueService revenueService;

    @Transactional(readOnly = true)
    public String export(String kind, LocalDateTime from, LocalDateTime to) {
        StringBuilder csv = new StringBuilder("﻿");
        switch (kind) {
            case "members" -> members(csv);
            case "billing" -> billing(csv);
            case "payments" -> payments(csv, from, to);
            case "invoices" -> invoices(csv, from, to);
            case "revenue" -> revenue(csv);
            default -> throw new BadRequestException("Bilinmeyen dışa aktarma: " + kind);
        }
        return csv.toString();
    }

    private void members(StringBuilder csv) {
        row(csv, "ID", "Ad", "Soyad", "E-posta", "Telefon", "Kayıt", "Etiketler");
        Map<Long, String> tags = memberTagRepository.findAll().stream().collect(Collectors.groupingBy(
                MemberTag::getMerchantId, Collectors.mapping(MemberTag::getTag, Collectors.joining(", "))));
        int rows = 0;
        for (int page = 0; rows < MAX_ROWS; page++) {
            Page<User> users = userRepository.findAll(PageRequest.of(page, PAGE, Sort.by("id")));
            for (User u : users) {
                row(csv, u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                        ts(u.getCreatedAt()), tags.get(u.getId()));
                rows++;
            }
            if (!users.hasNext()) {
                break;
            }
        }
    }

    private void billing(StringBuilder csv) {
        row(csv, "Üye ID", "Üye", "E-posta", "Paket", "Dönem", "Ödeme şekli", "Otomatik yenileme", "Durum",
                "Yenileme tutarı", "Borç", "Para birimi", "Son ödeme", "Vade", "Kalan gün", "Tahsilat durumu");
        for (int page = 0; ; page++) {
            AdminBillingDtos.AccountPage result = billingService.list(null, null, null, null, null, "DUE_ASC", page, 100);
            for (AdminBillingDtos.AccountRow r : result.content()) {
                row(csv, r.merchantId(), r.merchantName(), r.email(), r.packageName(), r.billingPeriod(), r.paymentStyle(),
                        r.autoRenew() ? "Evet" : "Hayır", r.standing(), r.renewalAmount(), r.outstandingAmount(), r.currency(),
                        ts(r.lastPaidAt()), ts(r.periodEndsAt()), r.daysUntilDue(), r.collectionStatus());
            }
            if (!result.hasNext() || page * 100 > MAX_ROWS) {
                break;
            }
        }
    }

    private void payments(StringBuilder csv, LocalDateTime from, LocalDateTime to) {
        row(csv, "Tarih", "Ödeme no", "Alıcı", "E-posta", "Tutar", "Tahsil edilen", "İade", "Para birimi", "Durum",
                "Tip", "Şekil", "Taksit", "Hata");
        PaymentSearchQuery query = PaymentSearchQuery.builder().createdFrom(from).createdTo(to).verificationOnly(false).build();
        int rows = 0;
        for (int page = 0; rows < MAX_ROWS; page++) {
            var result = paymentService.searchPayments(query, PageRequest.of(page, PAGE, Sort.by(Sort.Direction.DESC, "createdAt")));
            for (var p : result.getContent()) {
                row(csv, ts(p.getCreatedAt()), p.getConversationId(), p.getBuyerName(), p.getBuyerEmail(), p.getPrice(),
                        p.getPaidPrice(), p.getRefundedAmount(), p.getCurrency(), p.getStatus(), p.getPaymentType(),
                        p.getPaymentStyle(), p.getBankInstallmentCount(), p.getErrorMessage());
                rows++;
            }
            if (!result.hasNext()) {
                break;
            }
        }
    }

    private void invoices(StringBuilder csv, LocalDateTime from, LocalDateTime to) {
        row(csv, "Tarih", "Fatura no", "Tür", "Durum", "Üye ID", "Alıcı", "VKN/TCKN", "Vergi dairesi", "E-posta",
                "Açıklama", "Ara toplam", "KDV %", "KDV", "Toplam", "Para birimi", "Kesildi", "Hata");
        Specification<BillingInvoice> spec = (root, q, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                to == null ? cb.conjunction() : cb.lessThan(root.get("createdAt"), to));
        int rows = 0;
        for (int page = 0; rows < MAX_ROWS; page++) {
            Page<BillingInvoice> result = invoiceRepository.findAll(spec, PageRequest.of(page, PAGE, Sort.by(Sort.Direction.DESC, "createdAt")));
            for (BillingInvoice i : result) {
                row(csv, ts(i.getCreatedAt()), i.getInvoiceNumber(), i.getKind(), i.getStatus(), i.getMerchantId(),
                        i.getBuyerName(), i.getBuyerTaxId(), i.getBuyerTaxOffice(), i.getBuyerEmail(), i.getDescription(),
                        i.getSubtotal(), i.getVatRate(), i.getVatAmount(), i.getTotal(), i.getCurrency(),
                        ts(i.getIssuedAt()), i.getLastError());
                rows++;
            }
            if (!result.hasNext()) {
                break;
            }
        }
    }

    private void revenue(StringBuilder csv) {
        row(csv, "Ay", "Tahsil edilen", "İade", "Net", "Ödeme sayısı", "Yeni müşteri", "Kaybedilen", "Ay başı aktif", "MRR");
        for (RevenueService.Month m : revenueService.summary(24).months()) {
            row(csv, m.month(), m.collected(), m.refunded(), m.net(), m.payments(), m.newCustomers(), m.churned(),
                    m.activeAtStart(), m.mrr());
        }
    }

    private static String ts(LocalDateTime t) {
        return t == null ? null : TS.format(t);
    }

    static void row(StringBuilder csv, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(cell(cells[i]));
        }
        csv.append("\r\n");
    }

    /** Quotes when needed and defuses spreadsheet formulas (=, +, -, @ at the start). */
    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof java.math.BigDecimal d ? d.toPlainString().replace('.', ',') : Objects.toString(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)
                && !(value instanceof java.math.BigDecimal)) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
