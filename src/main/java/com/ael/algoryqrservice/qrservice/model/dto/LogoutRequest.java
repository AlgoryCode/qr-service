package com.ael.algoryqrservice.qrservice.model.dto;

import lombok.Data;

@Data
public class LogoutRequest {

    private String refreshToken;
}
