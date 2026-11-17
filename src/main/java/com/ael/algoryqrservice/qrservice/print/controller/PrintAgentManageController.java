package com.ael.algoryqrservice.qrservice.print.controller;

import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.branch.dto.BranchDtos;
import com.ael.algoryqrservice.qrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.qrservice.print.service.PrintAgentDeviceService;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/print-agent/manage")
@RequiredArgsConstructor
public class PrintAgentManageController {

    private final PrintAgentDeviceService deviceService;
    private final BranchService branchService;

    @GetMapping("/settings")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<PrintAgentDtos.SettingsResponse> settings(@RequestParam Long branchId) {
        return ResponseEntity.ok(deviceService.getSettings(branchId));
    }

    @PostMapping("/pairing-codes")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<PrintAgentDtos.PairingCodeResponse> createPairingCode(
            @Valid @RequestBody PrintAgentDtos.CreatePairingCodeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createPairingCode(request.getBranchId()));
    }

    @PostMapping("/api-keys")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<PrintAgentDtos.ApiKeyResponse> createApiKey(
            @Valid @RequestBody PrintAgentDtos.CreateApiKeyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createApiKey(request));
    }

    @GetMapping("/devices")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<List<PrintAgentDtos.DeviceResponse>> listDevices(@RequestParam Long branchId) {
        return ResponseEntity.ok(deviceService.listDevices(branchId));
    }

    @PutMapping("/devices/{deviceId}/enabled")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<PrintAgentDtos.DeviceResponse> setEnabled(
            @PathVariable Long deviceId,
            @RequestBody Map<String, Boolean> body
    ) {
        Boolean enabled = body == null ? null : body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(deviceService.setDeviceEnabled(deviceId, enabled));
    }

    @GetMapping("/jobs/failed")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<List<PrintAgentDtos.JobResponse>> failedJobs(@RequestParam Long branchId) {
        return ResponseEntity.ok(deviceService.listFailedJobs(branchId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<PrintAgentDtos.JobResponse> retryJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(deviceService.retryJobForOwner(jobId));
    }

    @PutMapping("/branches/{branchId}/print-kitchen")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<BranchDtos.Response> setPrintKitchen(
            @PathVariable Long branchId,
            @Valid @RequestBody PrintAgentDtos.SetPrintKitchenRequest request
    ) {
        Branch branch = deviceService.setPrintKitchenEnabled(branchId, request.getEnabled());
        return ResponseEntity.ok(branchService.getMine(branch.getId()));
    }
}
