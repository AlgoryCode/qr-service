package com.ael.algoryqrservice.qrservice.internal.controller;

import com.ael.algoryqrservice.qrservice.model.IntegrationJob;
import com.ael.algoryqrservice.qrservice.integration.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.qrservice.integration.service.IntegrationExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/integrations")
@RequiredArgsConstructor
public class InternalIntegrationController {

    private final IntegrationExportService exportService;
    private final ObjectMapper objectMapper;

    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> listJobs(@RequestParam String status) {
        List<String> statuses = Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        List<Map<String, Object>> body = exportService.listByStatuses(statuses).stream()
                .map(this::toJobBody)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/jobs/{jobId}/snapshot")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getSnapshot(@PathVariable UUID jobId) {
        return ResponseEntity.ok(objectMapper.convertValue(exportService.getSnapshot(jobId), Map.class));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(toJobBody(exportService.requireJob(jobId)));
    }

    @PatchMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> updateJob(
            @PathVariable UUID jobId,
            @RequestBody IntegrationPendingProductDtos.JobUpdateRequest request
    ) {
        return ResponseEntity.ok(toJobBody(exportService.updateJob(jobId, request)));
    }

    private Map<String, Object> toJobBody(IntegrationJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId());
        body.put("tenantId", job.getTenantId());
        body.put("menuId", job.getMenuId());
        body.put("direction", job.getDirection());
        body.put("status", job.getStatus());
        body.put("snapshotVersion", job.getSnapshotVersion());
        body.put("aiBatchId", job.getAiBatchId());
        body.put("aiInputFileId", job.getAiInputFileId());
        body.put("aiOutputFileId", job.getAiOutputFileId());
        body.put("errorMessage", job.getErrorMessage());
        return body;
    }
}
