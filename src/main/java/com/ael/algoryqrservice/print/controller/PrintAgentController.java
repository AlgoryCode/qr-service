package com.ael.algoryqrservice.print.controller;

import com.ael.algoryqrservice.print.dto.PrintAgentDtos;
import com.ael.algoryqrservice.print.service.PrintAgentDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/print-agent")
@RequiredArgsConstructor
public class PrintAgentController {

    private final PrintAgentDeviceService deviceService;

    @PostMapping("/devices/pair")
    public ResponseEntity<PrintAgentDtos.PairDeviceResponse> pair(
            @Valid @RequestBody PrintAgentDtos.PairDeviceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.pairDevice(request));
    }

    @PostMapping("/devices/connect")
    public ResponseEntity<PrintAgentDtos.ConnectResponse> connect(
            @Valid @RequestBody PrintAgentDtos.ConnectRequest request
    ) {
        return ResponseEntity.ok(deviceService.connect(request));
    }

    @PostMapping("/devices/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody(required = false) PrintAgentDtos.HeartbeatRequest request) {
        deviceService.heartbeat(request == null ? new PrintAgentDtos.HeartbeatRequest() : request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public ResponseEntity<PrintAgentDtos.PendingJobsResponse> listJobs(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(deviceService.listJobs(limit));
    }

    @GetMapping("/jobs/pending")
    public ResponseEntity<PrintAgentDtos.PendingJobsResponse> pending(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(deviceService.claimPending(limit));
    }

    @PostMapping("/jobs/{jobId}/ack")
    public ResponseEntity<Void> ack(
            @PathVariable Long jobId,
            @Valid @RequestBody PrintAgentDtos.AckRequest request
    ) {
        deviceService.ack(jobId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs/{jobId}/reprint")
    public ResponseEntity<PrintAgentDtos.JobResponse> reprint(@PathVariable Long jobId) {
        return ResponseEntity.ok(deviceService.reprintForDevice(jobId));
    }
}
