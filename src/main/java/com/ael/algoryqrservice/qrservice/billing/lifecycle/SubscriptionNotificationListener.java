package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanPackageResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PublicPackageCatalogService;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.qrservice.menu.service.MenuPublicAccessService;
import com.ael.algoryqrservice.qrservice.platform.service.NotificationPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Reacts to committed subscription changes: keeps public menus in line with the merchant's access
 * and tells the merchant by mail. Everything here is best effort and never undoes the change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionNotificationListener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"));

    private final MenuPublicAccessService menuPublicAccessService;
    private final AuthMerchantClient authMerchantClient;
    private final NotificationPublisherService notifications;
    private final PublicPackageCatalogService catalog;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SubscriptionEvent event) {
        syncMenus(event);
        try {
            notify(event);
        } catch (RuntimeException ex) {
            log.warn("subscription_notification_failed merchantId={} type={} error={}",
                    event.merchantId(), event.type(), ex.getMessage());
        }
    }

    private void syncMenus(SubscriptionEvent event) {
        switch (event.type()) {
            case TRIAL_STARTED, TRIAL_ENDED, PACKAGE_ACTIVATED, PACKAGE_RENEWED, PACKAGE_CHANGED,
                 PAYMENT_RECOVERED, PACKAGE_REFUNDED, DOWNGRADED_TO_FREE -> {
                try {
                    menuPublicAccessService.syncForMerchant(event.merchantId());
                } catch (RuntimeException ex) {
                    log.warn("menu_sync_failed merchantId={} error={}", event.merchantId(), ex.getMessage());
                }
            }
            default -> {
            }
        }
    }

    private void notify(SubscriptionEvent event) {
        String subject;
        String body;
        String packageName = packageName(event.packageCode());
        String previousName = packageName(event.previousPackageCode());
        String plansUrl = appUrl + "/hesabim/abonelik/paketler";
        switch (event.type()) {
            case TRIAL_ENDING -> {
                recipient(event).ifPresent(r -> notifications.publishTrialExpiryReminder(
                        eventId(event), r.email(), r.name(), packageName, format(event.endsAt()),
                        daysLeft(event.endsAt()), plansUrl));
                return;
            }
            case TRIAL_ENDED -> {
                subject = "Deneme süreniz sona erdi";
                body = "Deneme süreniz sona erdi ve hesabınız Ücretsiz pakete geçti. Menünüz yayında kalmaya devam ediyor; "
                        + "tüm özelliklere yeniden erişmek için bir paket seçebilirsiniz: " + plansUrl;
            }
            case PAYMENT_FAILED -> {
                subject = "Ödemeniz alınamadı";
                body = packageName + " paketinizin yenileme ödemesi alınamadı. Hizmetiniz "
                        + format(event.endsAt()) + " tarihine kadar kesintisiz devam ediyor. "
                        + "Kart bilgilerinizi güncelleyerek ödemeyi tamamlayabilirsiniz: "
                        + appUrl + "/hesabim/kayitli-kartlarim";
            }
            case PAYMENT_RECOVERED -> {
                subject = "Ödemeniz alındı";
                body = packageName + " paketinizin ödemesi alındı. Aboneliğiniz devam ediyor.";
            }
            case DOWNGRADED_TO_FREE -> {
                subject = "Hesabınız Ücretsiz pakete geçti";
                body = (previousName.isBlank() ? "Paketinizin" : previousName + " paketinizin")
                        + " süresi doldu ve hesabınız Ücretsiz pakete geçti. Verileriniz korunuyor; "
                        + "paketinizi yenilemek için: " + plansUrl;
            }
            case PACKAGE_REFUNDED -> {
                subject = "İadeniz işlendi";
                body = packageName + " paketiniz için iade işlendi ve hesabınız Ücretsiz pakete geçti.";
            }
            default -> {
                return;
            }
        }
        recipient(event).ifPresent(r -> notifications.publishGeneric(eventId(event), r.email(), subject, body));
    }

    private java.util.Optional<Recipient> recipient(SubscriptionEvent event) {
        return authMerchantClient.findMerchantProfile(event.merchantId())
                .filter(profile -> profile.email() != null && !profile.email().isBlank())
                .map(profile -> new Recipient(profile.email(), displayName(profile)));
    }

    private static String displayName(AuthMerchantClient.MerchantProfileView profile) {
        String name = ((profile.firstName() == null ? "" : profile.firstName()) + " "
                + (profile.lastName() == null ? "" : profile.lastName())).trim();
        return name.isEmpty() ? profile.email() : name;
    }

    private String packageName(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return catalog.findByCode(code).map(PlanPackageResponse::getName).orElse(code);
        } catch (RuntimeException ex) {
            return code;
        }
    }

    /** Same event (same dedupe key) always maps to the same id, so retries never send two mails. */
    private static UUID eventId(SubscriptionEvent event) {
        String key = event.dedupeKey() != null
                ? event.dedupeKey()
                : event.merchantId() + ":" + event.type() + ":" + event.occurredAt();
        return UUID.nameUUIDFromBytes(("subscription:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static String format(Instant instant) {
        return instant == null ? "-" : DATE.format(instant.atZone(ZONE));
    }

    private static long daysLeft(Instant endsAt) {
        if (endsAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(Instant.now(), endsAt).toDays());
    }

    private record Recipient(String email, String name) {
    }
}
