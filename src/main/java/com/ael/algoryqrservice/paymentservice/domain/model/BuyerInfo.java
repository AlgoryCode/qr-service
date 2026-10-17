package com.ael.algoryqrservice.paymentservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerInfo {

    private String id;
    private String name;
    private String surname;
    private String gsmNumber;
    private String email;
    private String identityNumber;
    private String registrationAddress;
    private String ip;
    private String city;
    private String country;
    private String zipCode;
}
