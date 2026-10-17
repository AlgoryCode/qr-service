package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.application.dto.CardStorageSessionDto;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.CardStorageSession;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentTransaction;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.domain.model.StoreCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentRepositoryPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentGatewayProperties;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.ServiceNameValidator;
import com.ael.algoryqrservice.paymentservice.infrastructure.messaging.SourceMetadataConverter;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaytrMerchantOid;
import com.ael.algoryqrservice.paymentservice.infrastructure.paytr.PaymentGatewayRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardStorageService {

    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final ServiceNameValidator serviceNameValidator;
    private final SourceMetadataConverter sourceMetadataConverter;
    private final PaymentGatewayProperties paymentGatewayProperties;

    @Transactional
    public CardStorageSessionDto initiate(StoreCardCommand command) {
        log.info(
                "Card storage step=START accountId={} conversationId={} serviceName={} currency={}",
                command.getAccountId(),
                command.getConversationId(),
                command.getServiceName(),
                command.getCurrency()
        );
        serviceNameValidator.validate(command.getServiceName());
        log.info("Card storage step=SERVICE_VALIDATED accountId={} conversationId={}",
                command.getAccountId(), command.getConversationId());
        command.setConversationId(PaytrMerchantOid.sanitize(command.getConversationId()));
        validateConversationIdUniqueness(command.getConversationId());
        PaymentProvider provider = resolveProvider();
        log.info("Card storage step=PROVIDER_RESOLVED accountId={} conversationId={} provider={}",
                command.getAccountId(), command.getConversationId(), provider);
        CardStorageSession session = paymentGatewayRegistry.resolve(provider).prepareCardStorage(command);
        log.info(
                "Card storage step=FORM_PREPARED accountId={} conversationId={} actionUrl={} fieldNames={}",
                command.getAccountId(),
                command.getConversationId(),
                session.actionUrl(),
                session.fields().keySet()
        );
        PaymentTransaction transaction = buildInitiatedTransaction(command, provider);
        paymentRepositoryPort.save(transaction);
        log.info("Card storage step=INITIATED_PERSISTED accountId={} conversationId={} status={}",
                command.getAccountId(), command.getConversationId(), transaction.getStatus());
        log.info(
                "Card verification initiated. userId={} paymentId={}",
                command.getAccountId(),
                command.getConversationId()
        );
        return new CardStorageSessionDto(session.conversationId(), session.actionUrl(), session.fields());
    }

    private PaymentTransaction buildInitiatedTransaction(StoreCardCommand command, PaymentProvider provider) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = command.getPaidPrice() != null ? command.getPaidPrice() : command.getPrice();
        return PaymentTransaction.builder()
                .conversationId(command.getConversationId())
                .provider(provider)
                .accountId(command.getAccountId())
                .basketId(command.getConversationId())
                .price(command.getPrice())
                .paidPrice(amount)
                .refundedAmount(BigDecimal.ZERO)
                .currency(command.getCurrency())
                .status(PaymentStatus.INITIATED)
                .paymentType(PaymentType.CHECKOUT_FORM)
                .serviceName(command.getServiceName())
                .sourceReferenceId(command.getSourceReferenceId())
                .sourceMetadataJson(sourceMetadataConverter.toJson(null))
                .buyerEmail(command.getBuyer() == null ? null : command.getBuyer().getEmail())
                .buyerName(resolveBuyerName(command))
                .paymentStyle(PaymentStyle.ONE_TIME)
                .verificationOnly(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String resolveBuyerName(StoreCardCommand command) {
        if (command.getBuyer() == null) {
            return null;
        }
        String name = ((command.getBuyer().getName() == null ? "" : command.getBuyer().getName()) + " "
                + (command.getBuyer().getSurname() == null ? "" : command.getBuyer().getSurname())).trim();
        return name.isBlank() ? null : name;
    }

    private void validateConversationIdUniqueness(String conversationId) {
        if (paymentRepositoryPort.findByConversationId(conversationId).isPresent()) {
            throw new PaymentProcessingException(
                    "CONVERSATION_ID_EXISTS",
                    "Bu conversationId zaten kullanildi"
            );
        }
    }

    private PaymentProvider resolveProvider() {
        PaymentProvider configured = paymentGatewayProperties.defaultProvider();
        return configured == null ? PaymentProvider.PAYTR : configured;
    }
}
