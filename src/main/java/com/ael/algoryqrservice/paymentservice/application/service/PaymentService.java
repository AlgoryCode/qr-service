package com.ael.algoryqrservice.paymentservice.application.service;

import com.ael.algoryqrservice.paymentservice.application.dto.CheckoutFormResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.PaymentResultDto;
import com.ael.algoryqrservice.paymentservice.application.dto.RefundResultDto;
import com.ael.algoryqrservice.paymentservice.domain.model.CreatePaymentCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.InitializeCheckoutFormCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaytrNotificationCommand;
import com.ael.algoryqrservice.paymentservice.domain.model.RefundPaymentCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResultDto createDirectPayment(CreatePaymentCommand command);

    CheckoutFormResultDto initializeCheckoutForm(InitializeCheckoutFormCommand command);

    PaymentResultDto retrievePayment(String conversationId);

    Page<PaymentResultDto> searchPayments(PaymentSearchQuery query, Pageable pageable);

    RefundResultDto refundPayment(RefundPaymentCommand command);

    String handlePaytrNotification(PaytrNotificationCommand command);

    void refundPendingVerificationCharges();
}
