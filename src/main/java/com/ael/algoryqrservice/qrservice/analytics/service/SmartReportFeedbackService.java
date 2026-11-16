package com.ael.algoryqrservice.qrservice.analytics.service;

import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.qrservice.model.SmartReportFeedback;
import com.ael.algoryqrservice.qrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.qrservice.repository.SmartReportFeedbackRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores what the merchant thought of a finished smart report and turns those answers into a
 * revised report: the answers become focus areas and a length hint in the AI prompt.
 *
 * <p>The merchant first answers "did you like this report?" with YES or NO. A YES carries only an
 * optional comment; a NO carries the follow-up questions plus an optional note.
 */
@Service
@RequiredArgsConstructor
public class SmartReportFeedbackService {

    static final Set<String> HELPFUL = Set.of("YES", "NO");
    static final Set<String> FIT = Set.of("FITS", "PARTLY", "NOT_FIT");
    static final Set<String> DETAIL = Set.of("SHORTER", "SAME", "LONGER");
    private static final int MAX_ITEMS = 10;
    private static final int MAX_ITEM_LENGTH = 120;
    private static final int MAX_NOTE_LENGTH = 1000;

    private final SmartReportEventRepository eventRepository;
    private final SmartReportFeedbackRepository feedbackRepository;
    private final SmartReportService smartReportService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<SmartReportDtos.FeedbackResponse> find(Long merchantId, UUID jobId) {
        requireReport(merchantId, jobId);
        return feedbackRepository.findByProcessIdAndMerchantId(jobId, merchantId).map(this::toResponse);
    }

    @Transactional
    public SmartReportDtos.FeedbackResponse save(Long merchantId, UUID jobId, SmartReportDtos.FeedbackRequest request) {
        SmartReportEvent report = requireReport(merchantId, jobId);
        requireFinished(report);
        return toResponse(upsert(report, merchantId, jobId, request));
    }

    /** Saves the answers, then queues a new report over the same scope and period shaped by them (uses 1 credit). */
    @Transactional
    public SmartReportDtos.SmartReportAccepted revise(
            Long merchantId,
            UUID jobId,
            SmartReportDtos.FeedbackRequest request
    ) {
        SmartReportEvent report = requireReport(merchantId, jobId);
        requireFinished(report);
        if (!"NO".equals(choice(request == null ? null : request.helpful(), HELPFUL, "helpful"))) {
            throw new BadRequestException("Rapor yalnizca begenilmediginde yeniden olusturulabilir");
        }
        SmartReportFeedback feedback = upsert(report, merchantId, jobId, request);
        SmartReportDtos.Options options = revisionOptions(feedback);
        SmartReportDtos.SmartReportAccepted accepted;
        if (report.getMenuId() != null) {
            accepted = smartReportService.enqueue(
                    report.getMenuId(), merchantId, report.getFromDate(), report.getToDate(), report.getLocale(), options);
        } else if (report.getBranchId() != null) {
            accepted = smartReportService.enqueueForBranch(
                    report.getBranchId(), merchantId, report.getFromDate(), report.getToDate(), report.getLocale(), options);
        } else {
            accepted = smartReportService.enqueueForMerchant(
                    merchantId, report.getFromDate(), report.getToDate(), report.getLocale(), options);
        }
        feedback.setRevisionProcessId(accepted.jobId());
        feedbackRepository.save(feedback);
        return accepted;
    }

    SmartReportDtos.Options revisionOptions(SmartReportFeedback feedback) {
        List<String> focus = new ArrayList<>(readList(feedback.getFocusAreas()));
        List<String> unclear = readList(feedback.getUnclearSections());
        if (!unclear.isEmpty()) {
            focus.add("şu bölümleri daha açık, somut ve örnekli anlat: " + String.join("; ", unclear));
        }
        if ("NOT_FIT".equals(feedback.getRecommendationsFit()) || "PARTLY".equals(feedback.getRecommendationsFit())) {
            focus.add("önerileri bu işletmenin verisine dayanan, hemen uygulanabilir adımlar olarak yeniden yaz");
        }
        if ("NO".equals(feedback.getHelpful())) {
            focus.add("önceki rapor işletme sahibine faydalı gelmedi; genel ifadeler yerine rakamlarla somut çıkarımlar yap");
        }
        if (feedback.getNote() != null && !feedback.getNote().isBlank()) {
            focus.add("işletme sahibinin notu: " + feedback.getNote().trim());
        }
        if ("SHORTER".equals(feedback.getDetailLevel())) {
            focus.add("raporu öncekinden daha kısa ve öz tut");
        } else if ("LONGER".equals(feedback.getDetailLevel())) {
            focus.add("raporu öncekinden daha detaylı anlat");
        }
        return new SmartReportDtos.Options(null, null, focus.isEmpty() ? null : focus);
    }

    private SmartReportFeedback upsert(
            SmartReportEvent report,
            Long merchantId,
            UUID jobId,
            SmartReportDtos.FeedbackRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Geri bildirim bos olamaz");
        }
        SmartReportFeedback feedback = feedbackRepository.findByProcessIdAndMerchantId(jobId, merchantId)
                .orElse(null);
        if (feedback == null) {
            feedback = SmartReportFeedback.builder().processId(jobId).merchantId(merchantId).build();
            // First feedback of the week on a completed report earns one extra report credit for this week.
            LocalDate bonusWeek = smartReportService.currentBonusWeek();
            if (SmartReportEvent.STATUS_COMPLETED.equals(report.getStatus())
                    && !feedbackRepository.existsByMerchantIdAndBonusWeek(merchantId, bonusWeek)) {
                feedback.setBonusWeek(bonusWeek);
            }
        }
        String helpful = choice(request.helpful(), HELPFUL, "helpful");
        if (helpful == null) {
            throw new BadRequestException("Raporu begenip begenmediginizi secin");
        }
        feedback.setHelpful(helpful);
        boolean disliked = "NO".equals(helpful);
        // Follow-up questions only apply to a disliked report; a liked one keeps just the comment.
        feedback.setRecommendationsFit(disliked ? choice(request.recommendationsFit(), FIT, "recommendationsFit") : null);
        feedback.setDetailLevel(disliked ? choice(request.detailLevel(), DETAIL, "detailLevel") : null);
        feedback.setUnclearSections(disliked ? writeList(clean(request.unclearSections())) : null);
        feedback.setFocusAreas(disliked ? writeList(clean(request.focusAreas())) : null);
        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new BadRequestException("Not en fazla " + MAX_NOTE_LENGTH + " karakter olabilir");
        }
        feedback.setNote(note == null || note.isEmpty() ? null : note);
        return feedbackRepository.save(feedback);
    }

    private SmartReportEvent requireReport(Long merchantId, UUID jobId) {
        return eventRepository.findByProcessIdAndMerchantId(jobId, merchantId)
                .orElseThrow(() -> new NotFoundException("Akilli rapor bulunamadi: " + jobId));
    }

    private static void requireFinished(SmartReportEvent report) {
        String status = report.getStatus();
        if (!SmartReportEvent.STATUS_COMPLETED.equals(status) && !SmartReportEvent.STATUS_FAILED.equals(status)) {
            throw new BadRequestException("Rapor hazir olmadan geri bildirim verilemez");
        }
    }

    private static String choice(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("Gecersiz " + field + ": " + value);
        }
        return normalized;
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            unique.add(trimmed.length() > MAX_ITEM_LENGTH ? trimmed.substring(0, MAX_ITEM_LENGTH) : trimmed);
            if (unique.size() == MAX_ITEMS) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private String writeList(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private SmartReportDtos.FeedbackResponse toResponse(SmartReportFeedback feedback) {
        return new SmartReportDtos.FeedbackResponse(
                feedback.getProcessId(),
                feedback.getHelpful(),
                feedback.getRecommendationsFit(),
                readList(feedback.getUnclearSections()),
                readList(feedback.getFocusAreas()),
                feedback.getDetailLevel(),
                feedback.getNote(),
                feedback.getRevisionProcessId(),
                feedback.getUpdatedAt(),
                feedback.getBonusWeek() != null
        );
    }
}
