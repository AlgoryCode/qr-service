package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.ContentService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.application.EmailTemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Announcements, FAQ and e-mail templates. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final ContentService contentService;
    private final EmailTemplateService emailTemplateService;
    private final DashboardSecurityUtils securityUtils;

    @GetMapping("/api/v1/admin/announcements")
    public List<ContentService.AnnouncementView> announcements() {
        return contentService.listAnnouncements();
    }

    @PostMapping("/api/v1/admin/announcements")
    public ContentService.AnnouncementView createAnnouncement(@Valid @RequestBody ContentService.AnnouncementRequest request) {
        return contentService.saveAnnouncement(null, request, actor());
    }

    @PutMapping("/api/v1/admin/announcements/{id}")
    public ContentService.AnnouncementView updateAnnouncement(@PathVariable Long id,
                                                              @Valid @RequestBody ContentService.AnnouncementRequest request) {
        return contentService.saveAnnouncement(id, request, actor());
    }

    @DeleteMapping("/api/v1/admin/announcements/{id}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable Long id) {
        contentService.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/faq")
    public List<ContentService.FaqView> faq() {
        return contentService.listFaq(false);
    }

    @PostMapping("/api/v1/admin/faq")
    public ContentService.FaqView createFaq(@Valid @RequestBody ContentService.FaqRequest request) {
        return contentService.saveFaq(null, request, actor());
    }

    @PutMapping("/api/v1/admin/faq/{id}")
    public ContentService.FaqView updateFaq(@PathVariable Long id, @Valid @RequestBody ContentService.FaqRequest request) {
        return contentService.saveFaq(id, request, actor());
    }

    @DeleteMapping("/api/v1/admin/faq/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        contentService.deleteFaq(id);
        return ResponseEntity.noContent().build();
    }

    public record TemplateRequest(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 10000) String body,
                                  boolean enabled) {
    }

    @GetMapping("/api/v1/admin/email-templates")
    public List<EmailTemplateService.TemplateView> templates() {
        return emailTemplateService.list();
    }

    @PutMapping("/api/v1/admin/email-templates/{key}")
    public EmailTemplateService.TemplateView saveTemplate(@PathVariable String key, @Valid @RequestBody TemplateRequest request) {
        return emailTemplateService.save(key, request.subject(), request.body(), request.enabled(), actor());
    }

    @PostMapping("/api/v1/admin/email-templates/{key}/reset")
    public EmailTemplateService.TemplateView resetTemplate(@PathVariable String key) {
        return emailTemplateService.reset(key);
    }

    @PostMapping("/api/v1/admin/email-templates/{key}/preview")
    public EmailTemplateService.Rendered preview(@PathVariable String key, @Valid @RequestBody TemplateRequest request) {
        return emailTemplateService.preview(key, request.subject(), request.body());
    }

    private String actor() {
        var me = securityUtils.getCurrentDashboardUser();
        return me.getUsername() != null ? me.getUsername() : me.getEmail();
    }
}
