package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializeCheckoutFormRequest {

    @NotBlank
    private String serviceName;

    private String sourceReferenceId;

    private Map<String, Object> sourceMetadata;

    @NotBlank
    private String conversationId;

    @NotBlank
    private String locale;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal paidPrice;

    @NotBlank
    private String currency;

    private PaymentStyle paymentStyle;

    private PaymentProvider provider;

    private Integer subscriptionCycleCount;

    private Integer billingIntervalMonths;

    @NotBlank
    private String basketId;

    @NotBlank
    private String paymentGroup;

    private List<Integer> enabledInstallments;

    @NotNull
    @Valid
    private BuyerRequest buyer;

    @NotNull
    @Valid
    private AddressRequest shippingAddress;

    @NotNull
    @Valid
    private AddressRequest billingAddress;

    @NotEmpty
    @Valid
    private List<BasketItemRequest> basketItems;
}
