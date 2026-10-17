package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreatePaymentRequest {

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

    @NotNull
    private PaymentStyle paymentStyle;

    private PaymentProvider provider;

    @Min(2)
    @Max(12)
    private Integer bankInstallmentCount;

    @Min(2)
    @Max(120)
    private Integer subscriptionCycleCount;

    @Min(1)
    @Max(24)
    private Integer billingIntervalMonths;

    @NotBlank
    private String basketId;

    @NotBlank
    private String paymentChannel;

    @NotBlank
    private String paymentGroup;

    private String callbackUrl;

    @Valid
    private PaymentCardRequest paymentCard;

    private Long paymentMethodId;

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
