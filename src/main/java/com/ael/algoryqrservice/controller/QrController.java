package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.QrType;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.service.QrService;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
public class QrController {
    private final QrService qrService;


    @PostMapping("/create")
    public ResponseEntity<?> createQr(@RequestBody QrRequest req) throws IOException, WriterException {
        return ResponseEntity.ok(qrService.createQR(req));
    }
}
