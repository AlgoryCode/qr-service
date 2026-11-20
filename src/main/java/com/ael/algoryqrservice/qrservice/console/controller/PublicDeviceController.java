package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.DevicePublicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The device QR page, open to anyone with the device's key (under the public menu prefix, which needs no sign-in).
 * The add-on is checked inside the service against the device's merchant.
 */
@RestController
@RequestMapping("/api/v1/menu/public/devices")
@RequiredArgsConstructor
public class PublicDeviceController {

    private final DevicePublicService publicService;

    @GetMapping("/{token}")
    public ResponseEntity<ConsoleDtos.PublicDeviceResponse> device(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicService.device(token));
    }

    @PostMapping("/{token}/extension-request")
    public ResponseEntity<ConsoleDtos.PublicDeviceResponse> requestExtension(
            @PathVariable String token,
            @Valid @RequestBody ConsoleDtos.PublicExtensionRequest request
    ) {
        return ResponseEntity.ok(publicService.requestExtension(token, request));
    }
}
