package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.qrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.qrservice.purchase.lifecycle.RemoteSubscriptionCanceller;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * Pays back a package bought while another paid package was still active. The payment handler
 * only marks the purchase; the refund runs after that commit so a provider call never holds the
 * payment transaction open. A refund that fails stays PENDING and is retried by the refund
 * reconcile job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicatePackageRefunder {

    public record Requested(Long purchaseId) {
    }

    private final PurchaseRepository purchaseRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final RemoteSubscriptionCanceller remoteSubscriptionCanceller;
    private final PurchaseLogService purchaseLogService;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(Requested event) {
        refund(event.purchaseId());
    }

    public static boolean isDuplicate(Purchase purchase) {
        return purchase != null && purchase.getCancellationReason() == PurchaseCancellationReason.DUPLICATE_PACKAGE;
    }

    /** Returns true once the provider accepted the refund. Safe to call again while still pending. */
    public boolean refund(Long purchaseId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Purchase purchase = tx.execute(status -> purchaseRepository.findById(purchaseId).orElse(null));
        if (!isDuplicate(purchase) || purchase.getRefundStatus() != RefundStatus.PENDING) {
            return false;
        }
        tx.executeWithoutResult(status -> remoteSubscriptionCanceller.cancelIfNeeded(purchase));
        BigDecimal refunded;
        try {
            BillingPaymentDtos.RefundablePayment refundable = paymentServiceClient.getRefundablePayment(
                    purchase.getMerchantId(), purchase.getPaymentConversationId());
            BigDecimal remaining = refundable.remaining() == null ? BigDecimal.ZERO : refundable.remaining();
            if (remaining.signum() > 0) {
                paymentServiceClient.refundPayment(
                        purchase.getMerchantId(), purchase.getPaymentConversationId(), remaining, null);
                refunded = remaining;
            } else {
                // Already paid back (e.g. an earlier attempt whose bookkeeping failed).
                refunded = refundable.refundedAmount() == null ? purchase.getPrice() : refundable.refundedAmount();
            }
        } catch (RuntimeException exception) {
            log.error("duplicate_package_refund_failed purchaseId={} merchantId={} amount={} reason={}",
                    purchaseId, purchase.getMerchantId(), purchase.getPrice(), exception.getMessage());
            return false;
        }
        tx.executeWithoutResult(status -> purchaseRepository.findByIdForUpdate(purchaseId).ifPresent(locked -> {
            locked.setRefundStatus(RefundStatus.COMPLETED);
            locked.setRefundedAt(AppTime.nowLocal());
            locked.setRefundPendingAt(null);
            purchaseRepository.save(locked);
            purchaseLogService.log(
                    locked.getId(),
                    locked.getMerchantId(),
                    PurchaseLogAction.PURCHASE_REFUND_COMPLETED,
                    locked.getPackageName() + " ikinci paket ödemesi iade edildi: " + refunded
            );
        }));
        log.info("duplicate_package_refunded purchaseId={} merchantId={} amount={}",
                purchaseId, purchase.getMerchantId(), refunded);
        return true;
    }
}
