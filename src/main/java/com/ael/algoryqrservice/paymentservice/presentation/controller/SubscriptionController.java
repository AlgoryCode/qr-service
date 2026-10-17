package com.ael.algoryqrservice.paymentservice.presentation.controller;

import com.ael.algoryqrservice.paymentservice.application.service.SubscriptionService;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.BootstrapSubscriptionRequest;
import com.ael.algoryqrservice.paymentservice.presentation.mapper.PaymentPresentationMapper;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.ChangeSubscriptionCardRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;
    private final PaymentPresentationMapper paymentPresentationMapper;

    @PostMapping("/bootstrap")    public SubscriptionResponse bootstrap(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody BootstrapSubscriptionRequest request
    ) {
        return service.bootstrap(
                accountId,
                request.getServiceName(),
                request.getSourceReferenceId(),
                request.getConversationId(),
                request.getAmount(),
                request.getCurrency(),
                request.getBillingIntervalMonths(),
                request.getPaymentMethodId(),
                request.getNextChargeAt(),
                request.getSourceMetadata(),
                paymentPresentationMapper.toBuyerInfo(request.getBuyer()),
                paymentPresentationMapper.toAddressInfo(request.getShippingAddress()),
                paymentPresentationMapper.toAddressInfo(request.getBillingAddress())
        );
    }

    @GetMapping("/{id}")    public SubscriptionResponse get(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable String id
    ) {
        return service.get(accountId, id);
    }

    @PostMapping("/{id}/cancel")    public SubscriptionResponse cancel(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable String id
    ) {
        return service.cancel(accountId, id);
    }

    @PutMapping("/{id}/payment-method")    public SubscriptionResponse changePaymentMethod(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable String id,
            @Valid @RequestBody ChangeSubscriptionCardRequest request
    ) {
        return service.changeCard(accountId, id, request.paymentMethodId());
    }
}
