package com.ael.algoryqrservice.qrservice.purchase.lifecycle;

import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteSubscriptionCanceller {

    private final PaymentServiceClient paymentServiceClient;
    private final PurchaseRepository purchaseRepository;

    public void cancelIfNeeded(Purchase purchase) {
        if (purchase.getPaymentStyle() != PaymentStyle.SUBSCRIPTION) {
            return;
        }
        if (purchase.getSubscriptionId() == null || purchase.getSubscriptionId().isBlank()) {
            return;
        }
        if (purchase.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
            return;
        }
        try {
            paymentServiceClient.cancelSubscription(purchase.getUserId(), purchase.getSubscriptionId());
            purchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
            purchaseRepository.save(purchase);
        } catch (PaymentServiceException exception) {
            log.warn(
                    "Remote subscription cancel failed. purchaseId={} reason={}",
                    purchase.getId(),
                    exception.getMessage()
            );
        }
    }
}
