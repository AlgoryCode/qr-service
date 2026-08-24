package com.ael.algoryqrservice.integration.odeal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class OdealTestDtos {

    private OdealTestDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProxyResponse {
        private int statusCode;
        private Object body;
        private String rawBody;
    }
}
