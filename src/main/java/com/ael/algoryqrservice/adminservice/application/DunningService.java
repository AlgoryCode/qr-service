package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.BillingReminder;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.BillingReminder.Kind;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.BillingReminderRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.platform.service.NotificationPublisherService;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.billing.service.RecurringAddonBilling;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Payment reminder e-mails: the daily schedule and the panel's "send reminder" button. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DunningService {

    static final int OVERDUE_FOLLOW_UP_DAYS = 7;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"));

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final BillingReminderRepository reminderRepository;
    private final CollectionCaseRepository collectionCaseRepository;
    private final CollectionService collectionService;
    private final EmailTemplateService templates;
    private final NotificationPublisherService notifications;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    /** Which scheduled reminder (if any) a purchase is due for today. */
    static Optional<Kind> dueReminder(Purchase p, LocalDateTime now) {
        Standing standing = AdminBillingService.standing(p, now, 8);
        if (p.getExpiresAt() == null) {
            return Optional.empty();
        }
        long days = AdminBillingService.daysBetween(now, p.getExpiresAt());
        if (standing == Standing.DUE_SOON && !RecurringAddonBilling.renewsWithHost(p)) {
            if (days == 7) {
                return Optional.of(Kind.DUE_IN_7);
            }
            if (days <= 1 && days >= 0) {
                return Optional.of(Kind.DUE_IN_1);
            }
        }
        if (standing == Standing.OVERDUE && -days >= OVERDUE_FOLLOW_UP_DAYS) {
            return Optional.of(Kind.OVERDUE_7);
        }
        return Optional.empty();
    }

    /** Sends today's scheduled reminders; each one at most once per purchase and period. */
    @Transactional
    public int runScheduled() {
        LocalDateTime now = AppTime.nowLocal();
        List<Purchase> current = AdminBillingService.currentPerMerchant(
                purchaseRepository.findBillingStandingCandidates(now.minusDays(AdminBillingService.LAPSED_LOOKBACK_DAYS)));
        int sent = 0;
        for (Purchase p : current) {
            Optional<Kind> kind = dueReminder(p, now);
            if (kind.isEmpty() || reminderRepository.existsByPurchaseIdAndKindAndPeriodEndAndSentBy(
                    p.getId(), kind.get(), p.getExpiresAt(), BillingReminder.SYSTEM)) {
                continue;
            }
            try {
                if (send(p, kind.get(), BillingReminder.SYSTEM)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("billing_reminder_failed merchantId={} kind={} error={}", p.getMerchantId(), kind.get(), ex.getMessage());
            }
        }
        return sent;
    }

    /** Admin button: sends a reminder now and moves an early-stage collection case to REMINDED. */
    @Transactional
    public BillingReminder remindNow(Long merchantId, String actor) {
        LocalDateTime now = AppTime.nowLocal();
        Purchase p = AdminBillingService.currentPerMerchant(
                        purchaseRepository.findBillingStandingCandidates(now.minusDays(AdminBillingService.LAPSED_LOOKBACK_DAYS)).stream()
                                .filter(x -> merchantId.equals(x.getMerchantId())).toList())
                .stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Bu üyenin hatırlatılacak bir ödemesi yok"));
        if (!send(p, Kind.MANUAL, actor)) {
            throw new BadRequestException("Hatırlatma e-postası kapalı ya da üyenin e-postası yok");
        }
        collectionCaseRepository.findFirstByMerchantIdAndClosedAtIsNull(merchantId)
                .filter(c -> c.getStatus() == CollectionStatus.AWAITING_PAYMENT)
                .map(CollectionCase::getId)
                .ifPresent(id -> collectionService.record(id, CollectionStatus.REMINDED, "Hatırlatma e-postası gönderildi", actor));
        return reminderRepository.findTop20ByMerchantIdOrderBySentAtDesc(merchantId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<BillingReminder> history(Long merchantId) {
        return reminderRepository.findTop20ByMerchantIdOrderBySentAtDesc(merchantId);
    }

    private boolean send(Purchase p, Kind kind, String actor) {
        User user = userRepository.findById(p.getMerchantId())
                .orElseThrow(() -> new NotFoundException("Üye bulunamadı"));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        String key = switch (kind) {
            case DUE_IN_7, DUE_IN_1 -> EmailTemplateService.PAYMENT_DUE_SOON;
            case OVERDUE_7 -> EmailTemplateService.PAYMENT_OVERDUE;
            case MANUAL -> EmailTemplateService.PAYMENT_REMINDER;
        };
        LocalDateTime now = AppTime.nowLocal();
        Map<String, String> values = Map.of(
                "userName", Optional.ofNullable(user.getDisplayName()).filter(s -> !s.isBlank()).orElse("Değerli üyemiz"),
                "packageName", Optional.ofNullable(p.getPackageName()).orElse("Paketiniz"),
                "dueDate", p.getExpiresAt() == null ? "-" : DATE.format(p.getExpiresAt()),
                "daysLeft", p.getExpiresAt() == null ? "-" : String.valueOf(Math.max(AdminBillingService.daysBetween(now, p.getExpiresAt()), 0)),
                "amount", money(renewalAmount(p), p.getCurrency()),
                "payUrl", appUrl + "/hesabim/abonelik");
        var rendered = templates.render(key, values);
        if (rendered.isEmpty()) {
            return false;
        }
        UUID eventId = UUID.nameUUIDFromBytes(("reminder:" + p.getId() + ":" + kind + ":" + p.getExpiresAt() + ":"
                + (BillingReminder.SYSTEM.equals(actor) ? "" : now)).getBytes(StandardCharsets.UTF_8));
        try {
            notifications.publishGeneric(eventId, user.getEmail(), rendered.get().subject(), rendered.get().body());
        } catch (org.springframework.amqp.AmqpException ex) {
            throw new BadRequestException("E-posta gönderilemedi: bildirim kuyruğuna şu an ulaşılamıyor");
        }
        reminderRepository.save(BillingReminder.builder()
                .merchantId(p.getMerchantId()).purchaseId(p.getId()).kind(kind)
                .periodEnd(p.getExpiresAt()).email(user.getEmail())
                .sentBy(actor).sentAt(now).build());
        log.info("billing_reminder_sent merchantId={} kind={} by={}", p.getMerchantId(), kind, actor);
        return true;
    }

    static BigDecimal renewalAmount(Purchase p) {
        BigDecimal base = p.getRecurringPrice() != null ? p.getRecurringPrice() : p.getPrice();
        return base == null ? null : base.add(p.getAddonRecurringAmount() == null ? BigDecimal.ZERO : p.getAddonRecurringAmount());
    }

    private static String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR"));
        try {
            format.setCurrency(java.util.Currency.getInstance(currency == null ? "TRY" : currency));
        } catch (IllegalArgumentException ignored) {
            // keep TRY
        }
        return format.format(amount);
    }
}
