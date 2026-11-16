package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import com.ael.algoryqrservice.qrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.repository.BillingInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private BillingInvoiceRepository repository;
    private EInvoiceProvider provider;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        repository = mock(BillingInvoiceRepository.class);
        provider = mock(EInvoiceProvider.class);
        when(provider.name()).thenReturn("test");
        service = new InvoiceService(repository, provider);
    }

    @Test
    void recordsASaleWithVatIncludedInThePrice() {
        service.recordSale(purchase(), event("evt-1", "120.00"));

        ArgumentCaptor<BillingInvoice> saved = ArgumentCaptor.forClass(BillingInvoice.class);
        verify(repository).save(saved.capture());
        BillingInvoice invoice = saved.getValue();
        assertThat(invoice.getKind()).isEqualTo(BillingInvoice.Kind.SALE);
        assertThat(invoice.getStatus()).isEqualTo(BillingInvoice.Status.PENDING);
        assertThat(invoice.getTotal()).isEqualByComparingTo("120.00");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("20.00");
        assertThat(invoice.getBuyerName()).isEqualTo("Algory Gıda Ltd.");
        assertThat(invoice.getBuyerTaxId()).isEqualTo("1234567890");
        assertThat(invoice.getBuyerAddress()).isEqualTo("Bağdat Cd. 1, Kadıköy, İstanbul");
    }

    @Test
    void aReplayedPaymentEventIsRecordedOnce() {
        when(repository.existsByPaymentEventIdAndKind("evt-1", BillingInvoice.Kind.SALE)).thenReturn(true);

        service.recordSale(purchase(), event("evt-1", "120.00"));

        verify(repository, never()).save(any());
    }

    @Test
    void refundsGetTheirOwnDocument() {
        service.recordRefund(purchase(), event("evt-2", "60.00"));

        ArgumentCaptor<BillingInvoice> saved = ArgumentCaptor.forClass(BillingInvoice.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(BillingInvoice.Kind.REFUND);
        assertThat(saved.getValue().getDescription()).startsWith("İade: ");
    }

    @Test
    void issuedInvoicesKeepTheProviderNumber() {
        BillingInvoice invoice = BillingInvoice.builder().id(5L).status(BillingInvoice.Status.PENDING).build();
        when(provider.issue(invoice)).thenReturn(EInvoiceProvider.Result.issued("ref-1", "ALG2026000000001", "https://doc"));

        assertThat(service.issueOne(invoice)).isTrue();

        assertThat(invoice.getStatus()).isEqualTo(BillingInvoice.Status.ISSUED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("ALG2026000000001");
        assertThat(invoice.getAttempts()).isEqualTo(1);
        assertThat(invoice.getIssuedAt()).isNotNull();
    }

    @Test
    void aProviderErrorLeavesTheInvoiceForRetry() {
        BillingInvoice invoice = BillingInvoice.builder().id(5L).status(BillingInvoice.Status.PENDING).build();
        when(provider.issue(invoice)).thenThrow(new IllegalStateException("timeout"));

        assertThat(service.issueOne(invoice)).isFalse();

        assertThat(invoice.getStatus()).isEqualTo(BillingInvoice.Status.FAILED);
        assertThat(invoice.getLastError()).isEqualTo("timeout");
    }

    private static Purchase purchase() {
        Purchase purchase = new Purchase();
        purchase.setId(9L);
        purchase.setMerchantId(7L);
        purchase.setPackageName("Pro Paket");
        purchase.setCurrency("TRY");
        BillingSnapshot buyer = new BillingSnapshot();
        buyer.setLegalName("Algory Gıda Ltd.");
        buyer.setVkn("1234567890");
        buyer.setAddress("Bağdat Cd. 1");
        buyer.setDistrict("Kadıköy");
        buyer.setCity("İstanbul");
        purchase.setBillingSnapshot(buyer);
        return purchase;
    }

    private static PaymentCompletedEventDto event(String id, String amount) {
        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId(id);
        event.setAmount(new BigDecimal(amount));
        return event;
    }
}
