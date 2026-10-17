package com.ael.algoryqrservice.paymentservice.infrastructure.messaging.adapter;

import com.ael.algoryqrservice.paymentservice.domain.model.BasketItemInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.BuyerInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentNotificationPublisherPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PushNotificationProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.dto.NotificationRecipientsMessage;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.dto.NotificationRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqPaymentNotificationPublisherAdapter implements PaymentNotificationPublisherPort {

    private static final String SERVICE_NAME = "payment-service";
    private static final String MESSAGE_TYPE = "PAYMENT_CONFIRMATION";

    private final RabbitTemplate rabbitTemplate;
    private final PushNotificationProperties pushNotificationProperties;

    @Override
    public void publishPaymentSuccess(CreatePaymentCommand command, PaymentTransaction transaction) {
        BuyerInfo buyer = command.getBuyer();
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            log.warn(
                    "Payment success notification skipped due to missing buyer email. conversationId={}",
                    transaction.getConversationId()
            );
            return;
        }

        List<BasketItemInfo> basketItems = command.getBasketItems() == null
                ? Collections.emptyList()
                : command.getBasketItems();

        sendNotification(
                buyer.getEmail(),
                resolveCustomerName(buyer),
                transaction,
                basketItems
        );
    }

    @Override
    public void publishPaymentSuccess(PaymentTransaction transaction) {
        if (transaction.getBuyerEmail() == null || transaction.getBuyerEmail().isBlank()) {
            log.warn(
                    "Payment success notification skipped due to missing buyer email. conversationId={}",
                    transaction.getConversationId()
            );
            return;
        }

        String customerName = transaction.getBuyerName() == null || transaction.getBuyerName().isBlank()
                ? "Customer"
                : transaction.getBuyerName();

        sendNotification(
                transaction.getBuyerEmail(),
                customerName,
                transaction,
                Collections.emptyList()
        );
    }

    private void sendNotification(
            String email,
            String customerName,
            PaymentTransaction transaction,
            List<BasketItemInfo> basketItems
    ) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", customerName);
        templateData.put("conversationId", transaction.getConversationId());
        templateData.put("paymentId", transaction.getPaymentId());
        templateData.put("basketId", transaction.getBasketId());
        templateData.put("currency", transaction.getCurrency());
        templateData.put("price", transaction.getPrice());
        templateData.put("paidPrice", transaction.getPaidPrice());
        if (!basketItems.isEmpty()) {
            templateData.put("products", basketItems.stream().map(this::toProductMap).toList());
        }

        NotificationRequestMessage message = new NotificationRequestMessage(
                UUID.randomUUID(),
                pushNotificationProperties.channels(),
                SERVICE_NAME,
                MESSAGE_TYPE,
                new NotificationRecipientsMessage(email, List.of(), List.of()),
                pushNotificationProperties.paymentSubject(),
                templateData,
                true
        );

        rabbitTemplate.convertAndSend(
                pushNotificationProperties.messaging().exchange(),
                pushNotificationProperties.messaging().routingKey(),
                message
        );

        log.info("Payment success notification queued. conversationId={}", transaction.getConversationId());
    }

    private String resolveCustomerName(BuyerInfo buyer) {
        if (buyer == null) {
            return "Customer";
        }
        String name = buyer.getName() == null ? "" : buyer.getName().trim();
        String surname = buyer.getSurname() == null ? "" : buyer.getSurname().trim();
        String fullName = (name + " " + surname).trim();
        return fullName.isEmpty() ? "Customer" : fullName;
    }

    private Map<String, Object> toProductMap(BasketItemInfo item) {
        Map<String, Object> product = new HashMap<>();
        product.put("id", item.getId());
        product.put("name", item.getName());
        product.put("category1", item.getCategory1());
        product.put("category2", item.getCategory2());
        product.put("itemType", item.getItemType());
        product.put("price", item.getPrice());
        return product;
    }
}
