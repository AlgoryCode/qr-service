package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.QrNameRequest;
import com.ael.algoryqrservice.model.dto.QrNameResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.service.QrService;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
public class QrController {
    private final QrService qrService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserQrs(@PathVariable Long userId) {
        return ResponseEntity.ok(qrService.getUserQrs(userId));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createQr(@RequestBody QrRequest req) throws IOException, WriterException {
        return ResponseEntity.ok(qrService.createQR(req));
    }

    @PutMapping("/update/{qrId}")
    public ResponseEntity<?> updateQr(@PathVariable Long qrId, @RequestBody QrRequest req) throws IOException, WriterException {
        return ResponseEntity.ok(qrService.updateQr(qrId, req));
    }

    @PatchMapping("/update-name/{qrId}")
    public ResponseEntity<QrNameResponse> updateQrName(@PathVariable Long qrId, @RequestBody QrNameRequest req) {
        return ResponseEntity.ok(qrService.updateQrName(qrId, req));
    }

    @DeleteMapping("/delete/{qrId}")
    public ResponseEntity<String> deleteQr(@PathVariable Long qrId){
        qrService.deleteQrByQrId(qrId);
        return ResponseEntity.ok("Deleted Success");
    }


}
