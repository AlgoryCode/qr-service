package com.ael.algoryqrservice.client.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PaymentCardStorageSessionResponse {

    private String conversationId;
    private String actionUrl;
    private Map<String, String> fields;
}
