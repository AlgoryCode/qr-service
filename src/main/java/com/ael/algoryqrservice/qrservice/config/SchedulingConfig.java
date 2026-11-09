package com.ael.algoryqrservice.qrservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zamanlanmış işler (abonelik yenileme, ödeme mutabakatı, entegrasyon senkronu...) varsayılan olarak açıktır.
 * Aynı veritabanına bağlanan ikinci bir geliştirme instance'ı {@code APP_SCHEDULING_ENABLED=false} ile açılır,
 * böylece bu işler iki kez çalışmaz.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
