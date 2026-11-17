package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlatformFeedback;
import com.ael.algoryqrservice.qrservice.model.PlatformFeedbackMessage;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.PlatformFeedbackStatus;
import com.ael.algoryqrservice.qrservice.platform.service.NotificationPublisherService;
import com.ael.algoryqrservice.qrservice.repository.PlatformFeedbackMessageRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Conversation on a support ticket: member-visible replies (e-mailed) and internal notes. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final FeedbackLookup feedbackLookup;
    private final PlatformFeedbackMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EmailTemplateService templates;
    private final NotificationPublisherService notifications;

    public record MessageView(Long id, String authorName, String body, boolean internal, boolean emailed,
                              LocalDateTime createdAt) {
    }

    public record Summary(long open, long inProgress, long unassigned, long urgent, Double avgFirstResponseHours,
                          long resolvedLast30Days) {
    }

    @Transactional(readOnly = true)
    public List<MessageView> messages(Long feedbackId) {
        feedbackLookup.require(feedbackId);
        return messageRepository.findByFeedbackIdOrderByCreatedAtAsc(feedbackId).stream().map(SupportTicketService::view).toList();
    }

    @Transactional
    public MessageView reply(Long feedbackId, String body, boolean internal, String actor) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Mesaj boş olamaz");
        }
        PlatformFeedback ticket = feedbackLookup.require(feedbackId);
        LocalDateTime now = AppTime.nowLocal();
        boolean emailed = false;
        if (!internal) {
            emailed = email(ticket, body.trim());
            if (ticket.getFirstResponseAt() == null) {
                ticket.setFirstResponseAt(now);
            }
            if (ticket.getStatus() == PlatformFeedbackStatus.OPEN) {
                ticket.setStatus(PlatformFeedbackStatus.IN_PROGRESS);
            }
            ticket.setUpdatedAt(now);
            feedbackLookup.save(ticket);
        }
        PlatformFeedbackMessage saved = messageRepository.save(PlatformFeedbackMessage.builder()
                .feedbackId(feedbackId).authorName(actor).body(body.trim())
                .internal(internal).emailed(emailed).createdAt(now).build());
        return view(saved);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<PlatformFeedback> all = feedbackLookup.all();
        LocalDateTime now = AppTime.nowLocal();
        long open = all.stream().filter(t -> t.getStatus() == PlatformFeedbackStatus.OPEN).count();
        long inProgress = all.stream().filter(t -> t.getStatus() == PlatformFeedbackStatus.IN_PROGRESS).count();
        long unassigned = all.stream().filter(t -> t.getStatus() != PlatformFeedbackStatus.RESOLVED && t.getAssigneeId() == null).count();
        long urgent = all.stream().filter(t -> t.getStatus() != PlatformFeedbackStatus.RESOLVED && "URGENT".equals(t.getPriority())).count();
        Double avg = all.stream().filter(t -> t.getFirstResponseAt() != null && t.getCreatedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getFirstResponseAt()).toMinutes())
                .average().stream().map(m -> Math.round(m / 6.0) / 10.0).boxed().findFirst().orElse(null);
        long resolved30 = all.stream().filter(t -> t.getResolvedAt() != null && t.getResolvedAt().isAfter(now.minusDays(30))).count();
        return new Summary(open, inProgress, unassigned, urgent, avg, resolved30);
    }

    private boolean email(PlatformFeedback ticket, String body) {
        Optional<User> user = userRepository.findById(ticket.getMerchantId());
        if (user.isEmpty() || user.get().getEmail() == null || user.get().getEmail().isBlank()) {
            return false;
        }
        var rendered = templates.render(EmailTemplateService.SUPPORT_REPLY, Map.of(
                "userName", Optional.ofNullable(user.get().getDisplayName()).filter(s -> !s.isBlank()).orElse("Değerli üyemiz"),
                "ticketTitle", Optional.ofNullable(ticket.getTitle()).orElse("Destek talebi"),
                "reply", body));
        if (rendered.isEmpty()) {
            return false;
        }
        try {
            notifications.publishGeneric(UUID.randomUUID(), user.get().getEmail(), rendered.get().subject(), rendered.get().body());
            return true;
        } catch (org.springframework.amqp.AmqpException ex) {
            // The reply is still saved; the panel shows it as not e-mailed.
            log.warn("support_reply_email_failed ticket={} reason={}", ticket.getId(), ex.getMessage());
            return false;
        }
    }

    private static MessageView view(PlatformFeedbackMessage m) {
        return new MessageView(m.getId(), m.getAuthorName(), m.getBody(), m.isInternal(), m.isEmailed(), m.getCreatedAt());
    }

    /** Thin seam over the feedback repository so the service stays testable. */
    @Service
    @RequiredArgsConstructor
    public static class FeedbackLookup {
        private final com.ael.algoryqrservice.qrservice.repository.PlatformFeedbackRepository repository;

        public PlatformFeedback require(Long id) {
            return repository.findById(id).orElseThrow(() -> new NotFoundException("Talep bulunamadı"));
        }

        public void save(PlatformFeedback ticket) {
            repository.save(ticket);
        }

        public List<PlatformFeedback> all() {
            return repository.findAll();
        }
    }
}
