package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.FaqEntry;
import com.ael.algoryqrservice.qrservice.model.PlatformAnnouncement;
import com.ael.algoryqrservice.qrservice.model.PlatformAnnouncement.Audience;
import com.ael.algoryqrservice.qrservice.model.PlatformAnnouncement.Tone;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.FaqEntryRepository;
import com.ael.algoryqrservice.qrservice.repository.PlatformAnnouncementRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/** Member-facing content the admin panel manages: announcement banners and the FAQ. */
@Service
@RequiredArgsConstructor
public class ContentService {

    /** Only https links or paths inside the member app; never javascript: or data: URLs. */
    private static final Pattern SAFE_LINK = Pattern.compile("^(https://[^\\s]+|/[^\\s/][^\\s]*|/)$");

    private final PlatformAnnouncementRepository announcementRepository;
    private final FaqEntryRepository faqRepository;
    private final PurchaseRepository purchaseRepository;

    // ---- announcements ----

    public record AnnouncementRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 600) String body,
            @Size(max = 500) String linkUrl,
            @Size(max = 40) String linkLabel,
            @NotNull Tone tone,
            @NotNull Audience audience,
            @NotNull LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean dismissible,
            boolean active
    ) {
    }

    public record AnnouncementView(Long id, String title, String body, String linkUrl, String linkLabel, Tone tone,
                                   Audience audience, LocalDateTime startsAt, LocalDateTime endsAt, boolean dismissible,
                                   boolean active, boolean live, String createdBy, LocalDateTime createdAt) {
    }

    /** What a member's banner needs; no admin metadata. */
    public record MemberAnnouncement(Long id, String title, String body, String linkUrl, String linkLabel, Tone tone,
                                     boolean dismissible, LocalDateTime updatedAt) {
    }

    @Transactional(readOnly = true)
    public List<AnnouncementView> listAnnouncements() {
        LocalDateTime now = AppTime.nowLocal();
        return announcementRepository.findAllByOrderByStartsAtDesc().stream().map(a -> view(a, now)).toList();
    }

    @Transactional
    public AnnouncementView saveAnnouncement(Long id, AnnouncementRequest r, String actor) {
        if (r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) {
            throw new BadRequestException("Bitiş, başlangıçtan sonra olmalı");
        }
        String link = blank(r.linkUrl());
        if (link != null && !SAFE_LINK.matcher(link).matches()) {
            throw new BadRequestException("Bağlantı https:// ile ya da / ile başlamalı");
        }
        LocalDateTime now = AppTime.nowLocal();
        PlatformAnnouncement a = id == null ? PlatformAnnouncement.builder().createdBy(actor).createdAt(now).build()
                : announcementRepository.findById(id).orElseThrow(() -> new NotFoundException("Duyuru bulunamadı"));
        a.setTitle(r.title().trim());
        a.setBody(blank(r.body()));
        a.setLinkUrl(link);
        a.setLinkLabel(link == null ? null : blank(r.linkLabel()));
        a.setTone(r.tone());
        a.setAudience(r.audience());
        a.setStartsAt(r.startsAt());
        a.setEndsAt(r.endsAt());
        a.setDismissible(r.dismissible());
        a.setActive(r.active());
        a.setUpdatedAt(now);
        return view(announcementRepository.save(a), now);
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        announcementRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MemberAnnouncement> announcementsFor(Long merchantId) {
        LocalDateTime now = AppTime.nowLocal();
        List<PlatformAnnouncement> live = announcementRepository
                .findByActiveTrueAndStartsAtLessThanEqualOrderByStartsAtDesc(now).stream()
                .filter(a -> a.liveAt(now)).toList();
        if (live.isEmpty()) {
            return List.of();
        }
        boolean paying = merchantId != null && purchaseRepository.existsByMerchantIdAndStatusAndPurchaseTypeAndPriceGreaterThan(
                merchantId, PurchaseStatus.ACTIVE, PurchaseType.PAID, BigDecimal.ZERO);
        return live.stream()
                .filter(a -> matches(a.getAudience(), paying))
                .limit(3)
                .map(a -> new MemberAnnouncement(a.getId(), a.getTitle(), a.getBody(), a.getLinkUrl(), a.getLinkLabel(),
                        a.getTone(), a.isDismissible(), a.getUpdatedAt()))
                .toList();
    }

    static boolean matches(Audience audience, boolean paying) {
        return switch (audience) {
            case ALL -> true;
            case PAYING -> paying;
            case FREE -> !paying;
        };
    }

    // ---- FAQ ----

    public record FaqRequest(
            @NotBlank @Size(max = 60) String category,
            @NotBlank @Size(max = 240) String question,
            @NotBlank @Size(max = 8000) String answer,
            @Min(0) @Max(9999) int sortOrder,
            boolean published
    ) {
    }

    public record FaqView(Long id, String category, String question, String answer, int sortOrder, boolean published,
                          String updatedBy, LocalDateTime updatedAt) {
    }

    @Transactional(readOnly = true)
    public List<FaqView> listFaq(boolean publishedOnly) {
        return (publishedOnly ? faqRepository.findByPublishedTrueOrderByCategoryAscSortOrderAscIdAsc()
                : faqRepository.findAllByOrderByCategoryAscSortOrderAscIdAsc()).stream().map(ContentService::view).toList();
    }

    @Transactional
    public FaqView saveFaq(Long id, FaqRequest r, String actor) {
        FaqEntry e = id == null ? new FaqEntry()
                : faqRepository.findById(id).orElseThrow(() -> new NotFoundException("Soru bulunamadı"));
        e.setCategory(r.category().trim());
        e.setQuestion(r.question().trim());
        e.setAnswer(r.answer().trim());
        e.setSortOrder(r.sortOrder());
        e.setPublished(r.published());
        e.setUpdatedBy(actor);
        e.setUpdatedAt(AppTime.nowLocal());
        return view(faqRepository.save(e));
    }

    @Transactional
    public void deleteFaq(Long id) {
        faqRepository.deleteById(id);
    }

    private static AnnouncementView view(PlatformAnnouncement a, LocalDateTime now) {
        return new AnnouncementView(a.getId(), a.getTitle(), a.getBody(), a.getLinkUrl(), a.getLinkLabel(), a.getTone(),
                a.getAudience(), a.getStartsAt(), a.getEndsAt(), a.isDismissible(), a.isActive(), a.liveAt(now),
                a.getCreatedBy(), a.getCreatedAt());
    }

    private static FaqView view(FaqEntry e) {
        return new FaqView(e.getId(), e.getCategory(), e.getQuestion(), e.getAnswer(), e.getSortOrder(), e.isPublished(),
                e.getUpdatedBy(), e.getUpdatedAt());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
