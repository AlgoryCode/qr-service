package com.ael.algoryqrservice.qrservice.qr.dto;

import java.util.Map;

public record QrPublicResponse(String type, String name, String content, Map<String, Object> details) {
}
