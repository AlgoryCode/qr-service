package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotBlank
    private String contactName;

    @NotBlank
    private String city;

    @NotBlank
    private String country;

    @NotBlank
    private String address;

    private String zipCode;
}
