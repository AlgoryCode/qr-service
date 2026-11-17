package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AdminGrantService;
import com.ael.algoryqrservice.adminservice.application.AdminMemberService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Member 360: overview, internal notes, tags (support) and free grants (finance; under
 * {@code /users/{id}/grants} so AdminPermissions treats them like package changes).
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {

    private final AdminMemberService memberService;
    private final AdminGrantService grantService;
    private final com.ael.algoryqrservice.adminservice.application.AdminEntitlementService entitlementService;
    private final DashboardSecurityUtils securityUtils;

    @GetMapping("/api/v1/admin/members/{id}/overview")
    public AdminMemberDtos.Overview overview(@PathVariable Long id) {
        return memberService.overview(id);
    }

    @GetMapping("/api/v1/admin/members/{id}/notes")
    public List<AdminMemberDtos.NoteResponse> notes(@PathVariable Long id) {
        return memberService.notes(id);
    }

    @PostMapping("/api/v1/admin/members/{id}/notes")
    public ResponseEntity<AdminMemberDtos.NoteResponse> addNote(
            @PathVariable Long id, @Valid @RequestBody AdminMemberDtos.NoteRequest request) {
        DashboardUser me = securityUtils.getCurrentDashboardUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.addNote(
                id, request.body(), Boolean.TRUE.equals(request.pinned()), me.getId(), actorName(me)));
    }

    @PatchMapping("/api/v1/admin/members/{id}/notes/{noteId}")
    public AdminMemberDtos.NoteResponse updateNote(@PathVariable Long id, @PathVariable Long noteId,
                                                   @Valid @RequestBody AdminMemberDtos.NoteUpdateRequest request) {
        return memberService.updateNote(id, noteId, request.body(), request.pinned());
    }

    @DeleteMapping("/api/v1/admin/members/{id}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId) {
        memberService.deleteNote(id, noteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/members/tags")
    public List<AdminMemberDtos.TagCount> allTags() {
        return memberService.allTags();
    }

    @PutMapping("/api/v1/admin/members/{id}/tags")
    public List<String> setTags(@PathVariable Long id, @Valid @RequestBody AdminMemberDtos.TagsRequest request) {
        return memberService.setTags(id, request.tags(), actorName(securityUtils.getCurrentDashboardUser()));
    }

    @GetMapping("/api/v1/admin/grantable-products")
    public List<AdminMemberDtos.GrantableProduct> grantableProducts() {
        return grantService.grantableProducts();
    }

    @GetMapping("/api/v1/admin/users/{id}/entitlements")
    public com.ael.algoryqrservice.adminservice.application.AdminEntitlementService.View entitlements(@PathVariable Long id) {
        return entitlementService.forMerchant(id);
    }

    @GetMapping("/api/v1/admin/users/{id}/grants")
    public List<AdminMemberDtos.GrantResponse> grants(@PathVariable Long id) {
        return grantService.list(id);
    }

    @PostMapping("/api/v1/admin/users/{id}/grants")
    public ResponseEntity<AdminMemberDtos.GrantResponse> grant(
            @PathVariable Long id, @Valid @RequestBody AdminMemberDtos.GrantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(grantService.grant(id, request, actorName(securityUtils.getCurrentDashboardUser())));
    }

    @PostMapping("/api/v1/admin/users/{id}/grants/{grantId}/revoke")
    public AdminMemberDtos.GrantResponse revoke(@PathVariable Long id, @PathVariable Long grantId) {
        return grantService.revoke(id, grantId, actorName(securityUtils.getCurrentDashboardUser()));
    }

    static String actorName(DashboardUser user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }
}
