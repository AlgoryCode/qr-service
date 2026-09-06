package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.CampaignTemplate;
import com.ael.algoryqrservice.repository.CampaignTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures built-in campaign templates exist.
 * Flyway is disabled in this service, so an ApplicationRunner seeds idempotently on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignTemplateSeedRunner implements ApplicationRunner {

    private static final String STAMP_SCHEMA = """
            {"fields":[{"key":"targetProductIds","type":"productIds"},{"key":"requiredQuantity","type":"number"},{"key":"reward","type":"reward"}]}
            """.strip();

    private static final String SPEND_SCHEMA = """
            {"fields":[{"key":"thresholdAmount","type":"number"},{"key":"period","type":"enum","values":["WEEKLY","MONTHLY"]},{"key":"reward","type":"reward"}]}
            """.strip();

    private final CampaignTemplateRepository campaignTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureTemplate(
                "STAMP_CARD",
                "Damga kartı",
                "Belirli ürünlerden N adet alınca ödül ürün verilir.",
                "stamp",
                STAMP_SCHEMA,
                10
        );
        ensureTemplate(
                "SPEND_THRESHOLD",
                "Harcama eşiği",
                "Haftalık veya aylık harcama eşiğine ulaşınca ödül ürün verilir.",
                "spend",
                SPEND_SCHEMA,
                20
        );
    }

    private void ensureTemplate(
            String code,
            String name,
            String description,
            String icon,
            String configSchema,
            int sortOrder
    ) {
        if (campaignTemplateRepository.findByCode(code).isPresent()) {
            return;
        }
        campaignTemplateRepository.save(CampaignTemplate.builder()
                .code(code)
                .name(name)
                .description(description)
                .icon(icon)
                .configSchema(configSchema)
                .sortOrder(sortOrder)
                .build());
        log.info("Seeded campaign template {}", code);
    }
}
