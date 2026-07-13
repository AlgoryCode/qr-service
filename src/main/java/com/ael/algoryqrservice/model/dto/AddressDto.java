package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressDto {

    @NotBlank(message = "İletişim adı zorunludur")
    private String contactName;

    @NotBlank(message = "Şehir zorunludur")
    private String city;

    @NotBlank(message = "Ülke zorunludur")
    private String country;

    @NotBlank(message = "Adres zorunludur")
    private String address;

    private String zipCode;
}
