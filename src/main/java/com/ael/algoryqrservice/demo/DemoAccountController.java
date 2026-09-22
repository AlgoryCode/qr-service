package com.ael.algoryqrservice.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DemoAccountController {

    private final DemoProperties properties;

    @GetMapping("/demo-account")
    public DemoAccountResponse demoAccount() {
        if (!properties.isConfigured()) {
            return DemoAccountResponse.disabled();
        }
        return DemoAccountResponse.open(properties.normalizedEmail(), properties.getPassword());
    }
}
