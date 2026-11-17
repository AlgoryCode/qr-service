package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.EmailTemplate;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.EmailTemplateRepository;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-mail texts the panel can edit. Every key has a built-in default; an admin edit is stored as an
 * override and can be reset. Placeholders are written {{name}}.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    public static final String PAYMENT_DUE_SOON = "PAYMENT_DUE_SOON";
    public static final String PAYMENT_OVERDUE = "PAYMENT_OVERDUE";
    public static final String PAYMENT_REMINDER = "PAYMENT_REMINDER";
    public static final String SUPPORT_REPLY = "SUPPORT_REPLY";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z]+)\\s*}}");

    public record Definition(String key, String name, String description, List<String> variables,
                             String subject, String body) {
    }

    public record Rendered(String subject, String body) {
    }

    public record TemplateView(String key, String name, String description, List<String> variables,
                               String subject, String body, boolean enabled, boolean customized,
                               String defaultSubject, String defaultBody, String updatedBy,
                               java.time.LocalDateTime updatedAt) {
    }

    static final Map<String, Definition> DEFAULTS = new LinkedHashMap<>();

    static {
        put(new Definition(PAYMENT_DUE_SOON, "Ödeme yaklaşıyor",
                "Kendiliğinden yenilenmeyen paketin bitmesine 7 ve 1 gün kala gider.",
                List.of("userName", "packageName", "dueDate", "daysLeft", "payUrl"),
                "{{packageName}} paketiniz {{daysLeft}} gün sonra bitiyor",
                """
                Merhaba {{userName}},

                {{packageName}} paketiniz {{dueDate}} tarihinde sona eriyor. Menünüzün ve QR kodlarınızın kesintisiz çalışması için paketinizi yenileyin:
                {{payUrl}}

                Teşekkürler,
                Algory QR"""));
        put(new Definition(PAYMENT_OVERDUE, "Ödeme gecikti",
                "Yenileme ödemesi alınamadıktan 7 gün sonra hâlâ ödenmemişse gider.",
                List.of("userName", "packageName", "amount", "dueDate", "payUrl"),
                "Ödemeniz hâlâ alınamadı: {{packageName}}",
                """
                Merhaba {{userName}},

                {{dueDate}} tarihli {{amount}} tutarındaki {{packageName}} yenileme ödemeniz alınamadı. Paketiniz kısa süre içinde kısıtlanabilir.
                Kart bilgilerinizi güncelleyip ödemeyi tamamlamak için:
                {{payUrl}}

                Sorunuz varsa bu e-postayı yanıtlayabilirsiniz.
                Algory QR"""));
        put(new Definition(PAYMENT_REMINDER, "Ödeme hatırlatma (elle)",
                "Yönetici panelinden \"Hatırlatma gönder\" ile gider.",
                List.of("userName", "packageName", "amount", "dueDate", "payUrl"),
                "Ödeme hatırlatması: {{packageName}}",
                """
                Merhaba {{userName}},

                {{packageName}} paketinizin {{amount}} tutarındaki ödemesi bekleniyor (vade: {{dueDate}}).
                Ödemeyi tamamlamak için: {{payUrl}}

                Ödemeyi yaptıysanız bu mesajı dikkate almayın.
                Algory QR"""));
        put(new Definition(SUPPORT_REPLY, "Destek yanıtı",
                "Destek talebine üyeye görünür bir yanıt yazıldığında gider.",
                List.of("userName", "ticketTitle", "reply"),
                "Talebiniz yanıtlandı: {{ticketTitle}}",
                """
                Merhaba {{userName}},

                "{{ticketTitle}}" başlıklı talebinize yanıtımız:

                {{reply}}

                Bu e-postayı yanıtlayarak bize yazmaya devam edebilirsiniz.
                Algory QR"""));
    }

    private static void put(Definition definition) {
        DEFAULTS.put(definition.key(), definition);
    }

    private final EmailTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        Map<String, EmailTemplate> overrides = new LinkedHashMap<>();
        repository.findAll().forEach(t -> overrides.put(t.getKey(), t));
        return DEFAULTS.values().stream().map(d -> view(d, overrides.get(d.key()))).toList();
    }

    /** The text to send, or empty when an admin switched this e-mail off. */
    @Transactional(readOnly = true)
    public Optional<Rendered> render(String key, Map<String, String> values) {
        Definition definition = definition(key);
        Optional<EmailTemplate> override = repository.findById(key);
        if (override.isPresent() && !override.get().isEnabled()) {
            return Optional.empty();
        }
        String subject = override.map(EmailTemplate::getSubject).orElse(definition.subject());
        String body = override.map(EmailTemplate::getBody).orElse(definition.body());
        return Optional.of(new Rendered(fill(subject, values), fill(body, values)));
    }

    @Transactional
    public TemplateView save(String key, String subject, String body, boolean enabled, String actor) {
        Definition definition = definition(key);
        List<String> unknown = PLACEHOLDER.matcher(subject + "\n" + body).results()
                .map(m -> m.group(1))
                .filter(v -> !definition.variables().contains(v))
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Bilinmeyen alan: " + String.join(", ", unknown));
        }
        EmailTemplate saved = repository.save(EmailTemplate.builder()
                .key(key).subject(subject.trim()).body(body).enabled(enabled)
                .updatedBy(actor).updatedAt(AppTime.nowLocal()).build());
        return view(definition, saved);
    }

    @Transactional
    public TemplateView reset(String key) {
        Definition definition = definition(key);
        repository.deleteById(key);
        return view(definition, null);
    }

    /** Sample rendering with example values, for the editor preview. */
    public Rendered preview(String key, String subject, String body) {
        Definition definition = definition(key);
        Map<String, String> sample = new LinkedHashMap<>();
        for (String v : definition.variables()) {
            sample.put(v, switch (v) {
                case "userName" -> "Ayşe Yılmaz";
                case "packageName" -> "Ultimate";
                case "dueDate" -> "1 Aralık 2026";
                case "daysLeft" -> "7";
                case "amount" -> "1.499,00 ₺";
                case "payUrl" -> "https://app.algoryqr.com/hesabim/abonelik";
                case "ticketTitle" -> "Menü fotoğrafları yüklenmiyor";
                case "reply" -> "Sorunu giderdik; sayfayı yenileyip tekrar dener misiniz?";
                default -> v;
            });
        }
        return new Rendered(fill(subject, sample), fill(body, sample));
    }

    static String fill(String text, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Definition definition(String key) {
        Definition definition = DEFAULTS.get(key);
        if (definition == null) {
            throw new NotFoundException("Şablon bulunamadı");
        }
        return definition;
    }

    private static TemplateView view(Definition d, EmailTemplate o) {
        return new TemplateView(d.key(), d.name(), d.description(), d.variables(),
                o == null ? d.subject() : o.getSubject(),
                o == null ? d.body() : o.getBody(),
                o == null || o.isEnabled(),
                o != null,
                d.subject(), d.body(),
                o == null ? null : o.getUpdatedBy(),
                o == null ? null : o.getUpdatedAt());
    }
}
