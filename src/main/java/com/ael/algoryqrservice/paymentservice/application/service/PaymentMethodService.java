package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentMethodNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentCardInfo;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizeCardCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.TokenizedCard;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentMethodEntity;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository.PaymentMethodJpaRepository;
import com.ael.algoryqrservice.paymentservice.infrastructure.security.PaymentTokenCipher;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CreatePaymentMethodRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentMethodResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PaymentMethodService {

    private static final String PROVIDER = "PAYTR";

    private final PaymentMethodJpaRepository repository;
    private final PaymentGatewayPort gateway;
    private final PaymentTokenCipher tokenCipher;

    public PaymentMethodService(
            PaymentMethodJpaRepository repository,
            @org.springframework.beans.factory.annotation.Qualifier("paytrPaymentGateway") PaymentGatewayPort gateway,
            PaymentTokenCipher tokenCipher
    ) {
        this.repository = repository;
        this.gateway = gateway;
        this.tokenCipher = tokenCipher;
    }

    @Transactional
    public PaymentMethodResponse create(String accountId, CreatePaymentMethodRequest request) {
        requireAccount(accountId);
        log.info("Card tokenization started. accountId={}, provider={}", accountId, PROVIDER);
        String existingCardUserKey = repository.findFirstByAccountIdAndDefaultMethodTrueAndDeletedAtIsNull(accountId)
                .map(PaymentMethodEntity::getCardUserKey)
                .map(tokenCipher::decrypt)
                .orElse(null);
        TokenizedCard tokenizedCard;
        try {
            tokenizedCard = gateway.tokenizeCard(new TokenizeCardCommand(
                    accountId,
                    request.email(),
                    request.alias(),
                    request.cardHolderName(),
                    request.cardNumber(),
                    request.expireMonth(),
                    request.expireYear(),
                    existingCardUserKey
            ));
            validateTokenization(tokenizedCard);
        } catch (PaymentProcessingException exception) {
            log.warn(
                    "Card tokenization failed. accountId={}, errorCode={}",
                    accountId,
                    exception.getErrorCode()
            );
            throw exception;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean makeDefault = !repository.existsByAccountIdAndDeletedAtIsNull(accountId);
        PaymentMethodEntity saved = repository.save(PaymentMethodEntity.builder()
                .accountId(accountId)
                .ownerReferenceId(accountId)
                .provider(PROVIDER)
                .cardUserKey(tokenCipher.encrypt(tokenizedCard.cardUserKey()))
                .cardToken(tokenCipher.encrypt(tokenizedCard.cardToken()))
                .cardAlias(tokenizedCard.alias())
                .cardAssociation(tokenizedCard.association())
                .cardFamily(tokenizedCard.family())
                .bankName(tokenizedCard.bankName())
                .lastFourDigits(tokenizedCard.lastFour())
                .defaultMethod(makeDefault)
                .createdAt(now)
                .updatedAt(now)
                .build());
        log.info(
                "Card tokenization succeeded. accountId={}, paymentMethodId={}, lastFour={}, default={}",
                accountId,
                saved.getId(),
                saved.getLastFourDigits(),
                makeDefault
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PaymentMethodResponse> list(String accountId, Pageable pageable) {
        requireAccount(accountId);
        return repository.findByAccountIdAndDeletedAtIsNull(accountId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse get(String accountId, Long id) {
        return toResponse(requireOwned(accountId, id));
    }

    @Transactional
    public PaymentMethodResponse rename(String accountId, Long id, String alias) {
        PaymentMethodEntity method = requireOwned(accountId, id);
        method.setCardAlias(alias);
        method.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(method));
    }

    @Transactional
    public PaymentMethodResponse makeDefault(String accountId, Long id) {
        PaymentMethodEntity method = requireOwned(accountId, id);
        repository.clearDefault(accountId);
        method.setDefaultMethod(true);
        method.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(method));
    }

    @Transactional
    public void delete(String accountId, Long id) {
        PaymentMethodEntity method = requireOwned(accountId, id);
        boolean wasDefault = method.isDefaultMethod();
        log.info(
                "Card deletion started. accountId={}, paymentMethodId={}, lastFour={}",
                accountId,
                id,
                method.getLastFourDigits()
        );
        gateway.deleteCard(
                tokenCipher.decrypt(method.getCardUserKey()),
                tokenCipher.decrypt(method.getCardToken())
        );
        method.setDefaultMethod(false);
        method.setDeletedAt(LocalDateTime.now());
        method.setUpdatedAt(LocalDateTime.now());
        repository.save(method);
        if (wasDefault) {
            repository.findFirstByAccountIdAndDeletedAtIsNullOrderById(accountId).ifPresent(replacement -> {
                replacement.setDefaultMethod(true);
                replacement.setUpdatedAt(LocalDateTime.now());
                repository.save(replacement);
            });
        }
        log.info("Card deleted. accountId={}, paymentMethodId={}", accountId, id);
    }

    @Transactional(readOnly = true)
    public PaymentCardInfo resolveToken(String accountId, Long id) {
        PaymentMethodEntity method = requireOwned(accountId, id);
        return PaymentCardInfo.builder()
                .cardUserKey(tokenCipher.decrypt(method.getCardUserKey()))
                .cardToken(tokenCipher.decrypt(method.getCardToken()))
                .registerCard(0)
                .build();
    }

    /**
     * Returns decrypted gateway tokens only inside the payment service. Tokens are
     * never exposed by an HTTP response; the scheduler uses this for card fallback.
     */
    @Transactional(readOnly = true)
    public List<StoredPaymentMethod> resolveTokens(String accountId) {
        requireAccount(accountId);
        return repository.findByAccountIdAndDeletedAtIsNullOrderByDefaultMethodDescIdAsc(accountId)
                .stream()
                .map(method -> new StoredPaymentMethod(
                        method.getId(),
                        method.isDefaultMethod(),
                        method.getLastFourDigits(),
                        PaymentCardInfo.builder()
                                .cardUserKey(tokenCipher.decrypt(method.getCardUserKey()))
                                .cardToken(tokenCipher.decrypt(method.getCardToken()))
                                .registerCard(0)
                                .build()
                ))
                .toList();
    }

    public record StoredPaymentMethod(
            Long id,
            boolean defaultMethod,
            String lastFourDigits,
            PaymentCardInfo card
    ) {
    }

    @Transactional
    public PaymentMethodEntity saveGatewayToken(
            String accountId,
            String alias,
            String cardUserKey,
            String cardToken,
            String lastFour
    ) {
        LocalDateTime now = LocalDateTime.now();
        boolean makeDefault = !repository.existsByAccountIdAndDeletedAtIsNull(accountId);
        return repository.save(PaymentMethodEntity.builder()
                .accountId(accountId)
                .ownerReferenceId(accountId)
                .provider(PROVIDER)
                .cardUserKey(tokenCipher.encrypt(cardUserKey))
                .cardToken(tokenCipher.encrypt(cardToken))
                .cardAlias(alias)
                .lastFourDigits(lastFour)
                .defaultMethod(makeDefault)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private PaymentMethodEntity requireOwned(String accountId, Long id) {
        requireAccount(accountId);
        return repository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
                .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }

    private void requireAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new PaymentProcessingException("ACCOUNT_REQUIRED", "Account identifier is required");
        }
    }

    private void validateTokenization(TokenizedCard card) {
        if (!"success".equalsIgnoreCase(card.status())) {
            throw new PaymentProcessingException(card.errorCode(), card.errorMessage());
        }
        if (card.cardUserKey() == null || card.cardToken() == null) {
            throw new PaymentProcessingException("TOKENIZATION_FAILED", "Provider did not return card tokens");
        }
    }

    private PaymentMethodResponse toResponse(PaymentMethodEntity method) {
        return new PaymentMethodResponse(
                method.getId(),
                method.getCardAlias(),
                method.getCardAssociation(),
                method.getCardFamily(),
                method.getBankName(),
                method.getLastFourDigits(),
                method.isDefaultMethod()
        );
    }
}
