package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AiMenuImportService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/menus/{menuId}/ai-import")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.AI_MENU_IMPORT_OWNER)
public class AiMenuImportController {

    private final AiMenuImportService aiMenuImportService;
    private final SecurityUtils securityUtils;

    @PostMapping("/jobs")
    public ResponseEntity<AiMenuImportDtos.JobAccepted> createJob(
            @PathVariable Long menuId,
            @Valid @RequestBody AiMenuImportDtos.CreateJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiMenuImportService.createJob(menuId, request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AiMenuImportDtos.JobResponse> getJob(
            @PathVariable Long menuId,
            @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(aiMenuImportService.getJob(menuId, jobId));
    }

    @GetMapping("/drafts")
    public ResponseEntity<Page<AiMenuImportDtos.DraftResponse>> listDrafts(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "WAITING_APPROVAL") String status,
            @RequestParam(required = false) UUID jobId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(aiMenuImportService.listDrafts(menuId, status, jobId, pageable));
    }

    @PatchMapping("/drafts/{draftId}")
    public ResponseEntity<AiMenuImportDtos.DraftResponse> updateDraft(
            @PathVariable Long menuId,
            @PathVariable UUID draftId,
            @RequestBody AiMenuImportDtos.DraftUpdateRequest request
    ) {
        return ResponseEntity.ok(aiMenuImportService.updateDraft(menuId, draftId, request));
    }

    @PostMapping("/drafts/{draftId}/approve")
    public ResponseEntity<AiMenuImportDtos.DraftResponse> approve(
            @PathVariable Long menuId,
            @PathVariable UUID draftId
    ) {
        return ResponseEntity.ok(aiMenuImportService.approve(menuId, draftId, securityUtils.getCurrentUserId()));
    }

    @PostMapping("/drafts/bulk-approve")
    public ResponseEntity<List<AiMenuImportDtos.DraftResponse>> bulkApprove(
            @PathVariable Long menuId,
            @Valid @RequestBody AiMenuImportDtos.BulkApproveRequest request
    ) {
        return ResponseEntity.ok(
                aiMenuImportService.bulkApprove(menuId, request.getDraftIds(), securityUtils.getCurrentUserId())
        );
    }

    @PostMapping("/drafts/{draftId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long menuId,
            @PathVariable UUID draftId,
            @RequestBody(required = false) AiMenuImportDtos.RejectRequest request
    ) {
        aiMenuImportService.reject(menuId, draftId, request == null ? new AiMenuImportDtos.RejectRequest() : request);
        return ResponseEntity.noContent().build();
    }
}
