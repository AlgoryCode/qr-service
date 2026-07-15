package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.GoogleOAuthProperties;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class GoogleAuthRedirectService {

    private final GoogleOAuthProperties properties;

    public String success(String ticket, GoogleAuthIntent intent) {
        return UriComponentsBuilder.fromUriString(properties.frontendCallbackUrl())
                .queryParam("ticket", ticket)
                .queryParam("intent", intent.value())
                .build()
                .encode()
                .toUriString();
    }

    public String failure() {
        return UriComponentsBuilder.fromUriString(properties.frontendCallbackUrl())
                .queryParam("error", "google_auth_failed")
                .build()
                .encode()
                .toUriString();
    }
}
