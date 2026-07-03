package com.ael.algoryqrservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private String message;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String registrationIpAddress;
    private String registrationUserAgent;
    private String registrationDevice;
    private String registrationDeviceType;
}
