package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A sale or refund document for one collected payment; the e-Arşiv provider issues it. */
@Entity
@Table(name = "billing_invoice", uniqueConstraints = {
        @UniqueConstraint(name = "uk_billing_invoice_event", columnNames = {"payment_event_id", "kind"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingInvoice {

    public enum Kind { SALE, REFUND }

    public enum Status { PENDING, ISSUED, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "payment_event_id", nullable = false, length = 128)
    private String paymentEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_tax_id", length = 32)
    private String buyerTaxId;

    @Column(name = "buyer_tax_office", length = 128)
    private String buyerTaxOffice;

    @Column(name = "buyer_address", length = 1024)
    private String buyerAddress;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(length = 32)
    private String provider;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @Column(name = "document_url", length = 1024)
    private String documentUrl;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
