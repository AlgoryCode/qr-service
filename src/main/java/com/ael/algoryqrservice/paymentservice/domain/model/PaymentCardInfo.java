package com.ael.algoryqrservice.paymentservice.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardInfo {

    @JsonIgnore
    @ToString.Exclude
    private String cardHolderName;

    @JsonIgnore
    @ToString.Exclude
    private String cardNumber;

    @JsonIgnore
    @ToString.Exclude
    private String expireMonth;

    @JsonIgnore
    @ToString.Exclude
    private String expireYear;

    @JsonIgnore
    @ToString.Exclude
    private String cvc;

    private Integer registerCard;

    @JsonIgnore
    @ToString.Exclude
    private String cardUserKey;

    @JsonIgnore
    @ToString.Exclude
    private String cardToken;
}
