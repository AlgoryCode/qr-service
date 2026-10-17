package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BootstrapSubscriptionRequest {

    @NotBlank
    private String serviceName;

    @NotBlank
    private String sourceReferenceId;

    @NotBlank
    private String conversationId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotBlank
    private String currency;

    private Integer billingIntervalMonths;

    @NotNull
    private Long paymentMethodId;

    @NotNull
    private LocalDateTime nextChargeAt;

    private Map<String, Object> sourceMetadata;

    @Valid
    private BuyerRequest buyer;

    @Valid
    private AddressRequest shippingAddress;

    @Valid
    private AddressRequest billingAddress;
}
