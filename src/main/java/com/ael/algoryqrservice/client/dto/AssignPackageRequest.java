package com.ael.algoryqrservice.client.dto;

public record AssignPackageRequest(
        String packageCode,
        int periodDays
) {
}
