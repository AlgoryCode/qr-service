package com.ael.algoryqrservice.qrservice.qr.controller;

import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.service.QrService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/features/QR_MENU")
@RequiredArgsConstructor
public class FeatureQrController {

    private final QrService qrService;
    private final SecurityUtils securityUtils;

    @PostMapping("/qrs")
    public ResponseEntity<?> createQr(@RequestBody QrRequest req) throws IOException, WriterException {
        Long merchantId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(qrService.createQR(req, merchantId));
    }
}
