package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String surname;

    private String gsmNumber;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String identityNumber;

    private String registrationAddress;

    @NotBlank
    private String ip;

    @NotBlank
    private String city;

    @NotBlank
    private String country;

    private String zipCode;
}
