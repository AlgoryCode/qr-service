package com.ael.algoryqrservice.qrservice.qr.controller;

import com.ael.algoryqrservice.qrservice.qr.dto.QrPublicResponse;
import com.ael.algoryqrservice.qrservice.qr.service.QrPublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr/public")
@RequiredArgsConstructor
public class QrPublicController {

    private final QrPublicService qrPublicService;

    @GetMapping("/{code}")
    public ResponseEntity<QrPublicResponse> resolve(
            @PathVariable String code,
            @RequestParam(defaultValue = "true") boolean track
    ) {
        return ResponseEntity.ok(qrPublicService.resolve(code, track));
    }
}
