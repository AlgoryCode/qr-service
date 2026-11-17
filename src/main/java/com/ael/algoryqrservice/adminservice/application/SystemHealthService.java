package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** One-screen health check for the panel: dependencies, migrations, jobs and error backlogs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<RabbitTemplate> rabbit;
    private final Environment environment;

    public record Check(String name, String status, String detail, Long latencyMs) {
    }

    public record Job(String name, LocalDateTime lastStartedAt, LocalDateTime lockedUntil, String lockedBy, boolean running) {
    }

    public record Backlog(String key, String label, long count, String severity) {
    }

    public record Health(String overall, LocalDateTime checkedAt, List<Check> checks, List<Job> jobs, List<Backlog> backlogs,
                         Map<String, Object> runtime) {
    }

    public Health check() {
        LocalDateTime now = AppTime.nowLocal();
        List<Check> checks = new ArrayList<>();
        checks.add(database());
        checks.add(queue());
        checks.add(migrations());

        List<Job> jobs = safe(() -> jdbc.query(
                "SELECT name, locked_at, locked_until, locked_by FROM scheduler_lock ORDER BY name",
                (rs, i) -> {
                    LocalDateTime until = rs.getObject("locked_until", LocalDateTime.class);
                    return new Job(rs.getString("name"), rs.getObject("locked_at", LocalDateTime.class), until,
                            shortOwner(rs.getString("locked_by")), until != null && until.isAfter(now));
                }), List.of());

        List<Backlog> backlogs = new ArrayList<>();
        backlog(backlogs, "invoices_failed", "Kesilemeyen fatura", "SELECT count(*) FROM billing_invoice WHERE status = 'FAILED'", "bad");
        backlog(backlogs, "invoices_pending", "Bekleyen fatura", "SELECT count(*) FROM billing_invoice WHERE status = 'PENDING'", "warn");
        backlog(backlogs, "payment_outbox", "Gönderilmemiş ödeme olayı",
                "SELECT count(*) FROM tbl_payment_event_outbox WHERE published_at IS NULL", "warn");
        backlog(backlogs, "price_plans_failed", "Uygulanamayan fiyat planı",
                "SELECT count(*) FROM catalog_schedule WHERE status = 'FAILED' OR (status = 'ACTIVE' AND last_error IS NOT NULL)", "bad");
        backlog(backlogs, "smart_reports_failed", "Başarısız akıllı rapor (24 saat)",
                "SELECT count(*) FROM tbl_smart_report_events WHERE status = 'FAILED' AND created_at > now() - interval '1 day'", "warn");
        backlog(backlogs, "tickets_open", "Yanıt bekleyen destek talebi",
                "SELECT count(*) FROM tbl_platform_feedback WHERE status = 'OPEN'", "warn");
        backlog(backlogs, "collections_open", "Açık tahsilat dosyası",
                "SELECT count(*) FROM billing_collection_case WHERE closed_at IS NULL", "info");

        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Map<String, Object> runtime = Map.of(
                "uptime", human(Duration.ofMillis(uptimeMs)),
                "heapUsedMb", memory.getUsed() / (1024 * 1024),
                "heapMaxMb", memory.getMax() / (1024 * 1024),
                "processors", Runtime.getRuntime().availableProcessors(),
                "java", System.getProperty("java.version"),
                "profiles", Arrays.toString(environment.getActiveProfiles()),
                "instance", ManagementFactory.getRuntimeMXBean().getName());

        String overall = checks.stream().anyMatch(c -> "DOWN".equals(c.status())) ? "DOWN"
                : checks.stream().anyMatch(c -> "WARN".equals(c.status()))
                || backlogs.stream().anyMatch(b -> "bad".equals(b.severity()) && b.count() > 0) ? "WARN" : "UP";
        return new Health(overall, now, checks, jobs, backlogs, runtime);
    }

    private Check database() {
        long start = System.nanoTime();
        try {
            String version = jdbc.queryForObject("SHOW server_version", String.class);
            String size = jdbc.queryForObject("SELECT pg_size_pretty(pg_database_size(current_database()))", String.class);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Check("Veritabanı", ms > 500 ? "WARN" : "UP", "PostgreSQL " + version + " · " + size, ms);
        } catch (RuntimeException ex) {
            return new Check("Veritabanı", "DOWN", ex.getClass().getSimpleName(), null);
        }
    }

    private Check queue() {
        RabbitTemplate template = rabbit.getIfAvailable();
        if (template == null) {
            return new Check("Mesaj kuyruğu (RabbitMQ)", "WARN", "Yapılandırılmamış", null);
        }
        long start = System.nanoTime();
        try {
            Boolean open = template.execute(channel -> channel.isOpen());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Check("Mesaj kuyruğu (RabbitMQ)", Boolean.TRUE.equals(open) ? "UP" : "DOWN",
                    "E-postalar ve modül teslimleri bu kuyruktan gider", ms);
        } catch (RuntimeException ex) {
            return new Check("Mesaj kuyruğu (RabbitMQ)", "DOWN",
                    "Bağlanılamıyor: e-postalar ve modül teslimleri gecikir", null);
        }
    }

    private Check migrations() {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT max(installed_rank) AS rank, count(*) FILTER (WHERE NOT success) AS failed FROM flyway_schema_history");
            String latest = jdbc.queryForObject(
                    "SELECT version || ' · ' || description FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
            long failed = ((Number) row.get("failed")).longValue();
            return new Check("Veritabanı sürümü", failed > 0 ? "DOWN" : "UP",
                    "Son: V" + latest + (failed > 0 ? " · " + failed + " başarısız" : ""), null);
        } catch (RuntimeException ex) {
            return new Check("Veritabanı sürümü", "WARN", "Okunamadı", null);
        }
    }

    private void backlog(List<Backlog> out, String key, String label, String sql, String severity) {
        Long count = safe(() -> jdbc.queryForObject(sql, Long.class), null);
        if (count != null) {
            out.add(new Backlog(key, label, count, severity));
        }
    }

    private static <T> T safe(java.util.function.Supplier<T> query, T fallback) {
        try {
            return query.get();
        } catch (RuntimeException ex) {
            // A table missing on this database (feature not deployed yet) is not a health failure.
            log.debug("health_query_skipped reason={}", ex.getMessage());
            return fallback;
        }
    }

    private static String shortOwner(String owner) {
        if (owner == null) {
            return null;
        }
        int colon = owner.indexOf(':');
        return colon > 0 ? owner.substring(0, colon) : owner;
    }

    static String human(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        return days > 0 ? days + " gün " + hours + " sa" : hours > 0 ? hours + " sa " + minutes + " dk" : minutes + " dk";
    }
}
