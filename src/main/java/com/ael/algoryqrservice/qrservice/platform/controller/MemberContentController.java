package com.ael.algoryqrservice.qrservice.platform.controller;

import com.ael.algoryqrservice.adminservice.application.ContentService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Announcement banners for the signed-in member, and the public FAQ. */
@RestController
@RequiredArgsConstructor
public class MemberContentController {

    private final ContentService contentService;
    private final SecurityUtils securityUtils;

    @GetMapping("/api/v1/announcements")
    public List<ContentService.MemberAnnouncement> announcements() {
        return contentService.announcementsFor(securityUtils.getCurrentMerchantId());
    }

    @GetMapping("/api/v1/faq")
    public List<ContentService.FaqView> faq() {
        return contentService.listFaq(true);
    }
}
