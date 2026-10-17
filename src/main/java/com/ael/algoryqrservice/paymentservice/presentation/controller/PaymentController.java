package com.ael.algoryqrservice.paymentservice.presentation.controller;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.application.service.PaymentService;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.domain.model.PaytrNotificationCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.CreatePaymentRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.InitializeCheckoutFormRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.request.RefundPaymentRequest;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.CheckoutFormResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.PaymentResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.RefundResponse;
import com.ael.algoryqrservice.paymentservice.presentation.mapper.PaymentPresentationMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;
    private final PaymentPresentationMapper paymentPresentationMapper;

    @PostMapping("/stored-card")    public ResponseEntity<PaymentResponse> chargeStoredCard(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        CreatePaymentCommand command = paymentPresentationMapper.toCreatePaymentCommand(request);
        command.setAccountId(accountId);
        PaymentResultDto result = paymentService.createDirectPayment(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentPresentationMapper.toPaymentResponse(result));
    }

    @PostMapping("/checkout-form")    public ResponseEntity<CheckoutFormResponse> initializeCheckoutForm(
            @RequestHeader(value = "X-Account-Id", required = false) String accountId,
            @Valid @RequestBody InitializeCheckoutFormRequest request
    ) {
        InitializeCheckoutFormCommand command = paymentPresentationMapper.toInitializeCheckoutFormCommand(request);
        command.setAccountId(accountId);
        CheckoutFormResultDto result = paymentService.initializeCheckoutForm(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentPresentationMapper.toCheckoutFormResponse(result));
    }

    @PostMapping(
            value = "/paytr/notification",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}
    )    public ResponseEntity<String> handlePaytrNotification(
            HttpServletRequest request,
            @RequestParam String merchant_oid,
            @RequestParam String status,
            @RequestParam String total_amount,
            @RequestParam String hash,
            @RequestParam(required = false) String payment_amount,
            @RequestParam(required = false) String failed_reason_code,
            @RequestParam(required = false) String failed_reason_msg,
            @RequestParam(required = false) String payment_type,
            @RequestParam(required = false) String installment_count,
            @RequestParam(required = false) String utoken,
            @RequestParam(required = false) String ctoken,
            @RequestParam(required = false) String masked_pan
    ) {
        String resolvedUtoken = firstParam(request, utoken, "utoken", "u_token", "user_token");
        String resolvedCtoken = firstParam(request, ctoken, "ctoken", "c_token", "card_token");
        String resolvedMaskedPan = firstParam(request, masked_pan, "masked_pan", "maskedPan", "payment_card_mask");
        log.info(
                "PayTR callback received. paymentId={} status={} totalAmountPresent={} hashPresent={} "
                        + "utokenPresent={} ctokenPresent={} maskedPanPresent={} parameterNames={}",
                merchant_oid,
                status,
                total_amount != null && !total_amount.isBlank(),
                hash != null && !hash.isBlank(),
                resolvedUtoken != null && !resolvedUtoken.isBlank(),
                resolvedCtoken != null && !resolvedCtoken.isBlank(),
                resolvedMaskedPan != null && !resolvedMaskedPan.isBlank(),
                request.getParameterMap().keySet()
        );
        String body = paymentService.handlePaytrNotification(new PaytrNotificationCommand(
                merchant_oid,
                status,
                total_amount,
                hash,
                payment_amount,
                failed_reason_code,
                failed_reason_msg,
                payment_type,
                installment_count,
                resolvedUtoken,
                resolvedCtoken,
                resolvedMaskedPan
        ));
        return ResponseEntity.ok(body);
    }

    private String firstParam(HttpServletRequest request, String primary, String... names) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        Map<String, String[]> params = request.getParameterMap();
        for (String name : names) {
            String[] values = params.get(name);
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        return null;
    }

    @GetMapping    public ResponseEntity<Page<PaymentResponse>> searchPayments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType paymentType,
            @RequestParam(required = false) PaymentStyle paymentStyle,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) Boolean verificationOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        PaymentSearchQuery query = PaymentSearchQuery.builder()
                .query(q)
                .status(status)
                .paymentType(paymentType)
                .paymentStyle(paymentStyle)
                .accountId(accountId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .verificationOnly(verificationOnly)
                .build();
        Page<PaymentResponse> result = paymentService.searchPayments(query, pageable)
                .map(paymentPresentationMapper::toPaymentResponse);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{conversationId}")    public ResponseEntity<PaymentResponse> retrievePayment(@PathVariable String conversationId) {
        PaymentResultDto result = paymentService.retrievePayment(conversationId);
        return ResponseEntity.ok(paymentPresentationMapper.toPaymentResponse(result));
    }

    @PostMapping("/refund")    public ResponseEntity<RefundResponse> refundPayment(@Valid @RequestBody RefundPaymentRequest request) {
        RefundPaymentCommand command = paymentPresentationMapper.toRefundPaymentCommand(request);
        RefundResultDto result = paymentService.refundPayment(command);
        return ResponseEntity.ok(paymentPresentationMapper.toRefundResponse(result));
    }
}
