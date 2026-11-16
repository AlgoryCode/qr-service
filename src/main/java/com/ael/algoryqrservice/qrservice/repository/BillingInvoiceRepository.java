package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, Long> {

    boolean existsByPaymentEventIdAndKind(String paymentEventId, BillingInvoice.Kind kind);

    List<BillingInvoice> findByStatusInAndAttemptsLessThanOrderByIdAsc(
            List<BillingInvoice.Status> statuses, int maxAttempts, Pageable pageable);

    List<BillingInvoice> findByMerchantIdOrderByCreatedAtDescIdDesc(Long merchantId, Pageable pageable);

    Optional<BillingInvoice> findByIdAndMerchantId(Long id, Long merchantId);
}
