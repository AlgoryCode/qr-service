package com.ael.algoryqrservice.qrservice.model.dto;

import java.util.List;

public record UserAccessProfile(
        String activePackage,
        List<String> products,
        List<String> scopes
) {
}
