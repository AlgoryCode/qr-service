package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.service.IntegrationExportService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/integrations")
@RequiredArgsConstructor
public class InternalIntegrationController {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final IntegrationExportService exportService;
    private final AiServiceProperties aiServiceProperties;

    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> listJobs(
            @RequestParam String status,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey
    ) {
        requireServiceKey(apiKey);
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
    public ResponseEntity<ObjectNode> getSnapshot(
            @PathVariable UUID jobId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey
    ) {
        requireServiceKey(apiKey);
        return ResponseEntity.ok(exportService.getSnapshot(jobId));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJob(
            @PathVariable UUID jobId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey
    ) {
        requireServiceKey(apiKey);
        return ResponseEntity.ok(toJobBody(exportService.requireJob(jobId)));
    }

    @PatchMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> updateJob(
            @PathVariable UUID jobId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody IntegrationPendingProductDtos.JobUpdateRequest request
    ) {
        requireServiceKey(apiKey);
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

    private void requireServiceKey(String apiKey) {
        String expected = aiServiceProperties.getApiKey();
        if (expected == null || expected.isBlank() || apiKey == null || !expected.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Geçersiz servis anahtarı");
        }
    }
}
