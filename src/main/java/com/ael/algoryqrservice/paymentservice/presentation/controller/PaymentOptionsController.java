package com.ael.algoryqrservice.paymentservice.presentation.controller;

import com.ael.algoryqrservice.paymentservice.domain.model.BinInformation;
import com.ael.algoryqrservice.paymentservice.domain.model.InstallmentOptions;
import com.ael.algoryqrservice.paymentservice.domain.port.PaymentGatewayPort;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/payment-options")
public class PaymentOptionsController {

    private final PaymentGatewayPort gateway;

    public PaymentOptionsController(
            @org.springframework.beans.factory.annotation.Qualifier("paytrPaymentGateway") PaymentGatewayPort gateway
    ) {
        this.gateway = gateway;
    }

    @GetMapping("/bin")    public BinInformation retrieveBin(
            @RequestParam @Pattern(regexp = "\\d{6,8}") String binNumber
    ) {
        return gateway.retrieveBin(binNumber);
    }

    @GetMapping("/installments")    public List<InstallmentOptions> retrieveInstallments(
            @RequestParam @Pattern(regexp = "\\d{6,8}") String binNumber,
            @RequestParam @DecimalMin("0.01") BigDecimal price,
            @RequestParam(defaultValue = "TRY") String currency
    ) {
        return gateway.retrieveInstallmentOptions(binNumber, price, currency);
    }
}
