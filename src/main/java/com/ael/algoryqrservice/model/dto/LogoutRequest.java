package com.ael.algoryqrservice.model.dto;

import lombok.Data;

@Data
public class LogoutRequest {

    private String refreshToken;
}
