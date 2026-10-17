package com.ael.algoryqrservice.paymentservice.presentation.controller;

import com.ael.algoryqrservice.paymentservice.application.service.CardStorageService;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentMethodService;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CardVerificationInitRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CreatePaymentMethodRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.RenamePaymentMethodRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.CardStorageSessionResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentMethodResponse;
import com.ael.algoryqrservice.paymentservice.presentation.mapper.PaymentPresentationMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService service;
    private final CardStorageService cardStorageService;
    private final PaymentPresentationMapper paymentPresentationMapper;

    @PostMapping("/verification")
    public ResponseEntity<CardStorageSessionResponse> initiateVerification(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody CardVerificationInitRequest request
    ) {
        log.info(
                "Card verification requested. merchantId={} paymentId={}",
                accountId,
                request.getConversationId()
        );
        try {
            var command = paymentPresentationMapper.toStoreCardCommand(accountId, request);
            var session = cardStorageService.initiate(command);
            log.info(
                    "Card verification session created. merchantId={} paymentId={}",
                    accountId,
                    session.conversationId()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CardStorageSessionResponse(
                            session.conversationId(),
                            session.actionUrl(),
                            session.fields()
                    ));
        } catch (RuntimeException exception) {
            log.warn(
                    "Card verification session failed. merchantId={} paymentId={} reason={}",
                    accountId,
                    request.getConversationId(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @PostMapping    public ResponseEntity<PaymentMethodResponse> create(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody CreatePaymentMethodRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(accountId, request));
    }

    @GetMapping    public Page<PaymentMethodResponse> list(
            @RequestHeader("X-Account-Id") String accountId,
            Pageable pageable
    ) {
        return service.list(accountId, pageable);
    }

    @GetMapping("/{id}")    public PaymentMethodResponse get(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable Long id
    ) {
        return service.get(accountId, id);
    }

    @PatchMapping("/{id}")    public PaymentMethodResponse rename(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable Long id,
            @Valid @RequestBody RenamePaymentMethodRequest request
    ) {
        return service.rename(accountId, id, request.alias());
    }

    @PostMapping("/{id}/default")    public PaymentMethodResponse makeDefault(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable Long id
    ) {
        return service.makeDefault(accountId, id);
    }

    @DeleteMapping("/{id}")    public ResponseEntity<Void> delete(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable Long id
    ) {
        service.delete(accountId, id);
        return ResponseEntity.noContent().build();
    }
}
