package com.ael.algoryqrservice.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestAuth {

    private HttpRequestAuth() {
    }

    public static String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }
}
