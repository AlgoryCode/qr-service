package com.ael.algoryqrservice.adminservice.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Usage of the AI features (dashboard assistant, smart reports) with an estimated cost. Nothing
 * records tokens yet, so cost is volume × a configured unit price and is labelled as an estimate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final JdbcTemplate jdbc;

    @Value("${admin.ai.unit-cost.assistant-question:0.01}")
    private BigDecimal assistantUnitCost;

    @Value("${admin.ai.unit-cost.smart-report:0.15}")
    private BigDecimal smartReportUnitCost;

    @Value("${admin.ai.unit-cost.currency:USD}")
    private String costCurrency;

    public record Day(String day, long assistant, long smartReports) {
    }

    public record Slice(String key, long count) {
    }

    public record TopMerchant(Long merchantId, String name, long assistant, long smartReports) {
    }

    public record Report(int days, boolean assistantTracked, boolean smartReportsTracked, long assistantQuestions,
                         long assistantFailed, Long assistantAvgMs, long smartReports, long smartReportsFailed,
                         BigDecimal estimatedCost, String costCurrency, BigDecimal assistantUnitCost,
                         BigDecimal smartReportUnitCost, List<Day> series, List<Slice> assistantOutcomes,
                         List<Slice> assistantTopics, List<Slice> smartReportStatuses, List<Slice> smartReportErrors,
                         List<TopMerchant> topMerchants) {
    }

    public Report report(int daysBack) {
        int days = Math.min(Math.max(daysBack, 7), 180);
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        boolean assistant = exists("tbl_assistant_usage_event");
        boolean reports = exists("tbl_smart_report_events");

        Map<String, long[]> perDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            perDay.put(d.toString(), new long[2]);
        }
        long questions = 0, failedQuestions = 0, reportCount = 0, failedReports = 0;
        Long avgMs = null;
        List<Slice> outcomes = List.of(), topics = List.of(), statuses = List.of(), errors = List.of();
        Map<Long, long[]> merchants = new LinkedHashMap<>();

        if (assistant) {
            jdbc.query("SELECT created_at::date AS d, count(*) AS n FROM tbl_assistant_usage_event WHERE created_at >= ? GROUP BY 1",
                    rs -> {
                        long[] slot = perDay.get(rs.getDate("d").toLocalDate().toString());
                        if (slot != null) {
                            slot[0] = rs.getLong("n");
                        }
                    }, from.atStartOfDay());
            outcomes = slices("SELECT outcome AS k, count(*) AS n FROM tbl_assistant_usage_event WHERE created_at >= ? GROUP BY 1 ORDER BY 2 DESC", from);
            topics = slices("SELECT topic AS k, count(*) AS n FROM tbl_assistant_usage_event WHERE created_at >= ? GROUP BY 1 ORDER BY 2 DESC LIMIT 8", from);
            questions = outcomes.stream().mapToLong(Slice::count).sum();
            failedQuestions = outcomes.stream().filter(s -> s.key() != null && !s.key().equalsIgnoreCase("ANSWERED")).mapToLong(Slice::count).sum();
            Double avg = jdbc.queryForObject("SELECT avg(response_ms) FROM tbl_assistant_usage_event WHERE created_at >= ?",
                    Double.class, from.atStartOfDay());
            avgMs = avg == null ? null : Math.round(avg);
            jdbc.query("SELECT merchant_id, count(*) AS n FROM tbl_assistant_usage_event WHERE created_at >= ? AND merchant_id IS NOT NULL "
                            + "GROUP BY 1 ORDER BY 2 DESC LIMIT 10",
                    rs -> {
                        merchants.computeIfAbsent(rs.getLong("merchant_id"), k -> new long[2])[0] = rs.getLong("n");
                    }, from.atStartOfDay());
        }
        if (reports) {
            jdbc.query("SELECT created_at::date AS d, count(*) AS n FROM tbl_smart_report_events WHERE created_at >= ? GROUP BY 1",
                    rs -> {
                        long[] slot = perDay.get(rs.getDate("d").toLocalDate().toString());
                        if (slot != null) {
                            slot[1] = rs.getLong("n");
                        }
                    }, from.atStartOfDay());
            statuses = slices("SELECT status AS k, count(*) AS n FROM tbl_smart_report_events WHERE created_at >= ? GROUP BY 1 ORDER BY 2 DESC", from);
            errors = slices("SELECT coalesce(error_code, 'UNKNOWN') AS k, count(*) AS n FROM tbl_smart_report_events "
                    + "WHERE created_at >= ? AND status = 'FAILED' GROUP BY 1 ORDER BY 2 DESC LIMIT 8", from);
            reportCount = statuses.stream().mapToLong(Slice::count).sum();
            failedReports = statuses.stream().filter(s -> "FAILED".equals(s.key())).mapToLong(Slice::count).sum();
            jdbc.query("SELECT merchant_id, count(*) AS n FROM tbl_smart_report_events WHERE created_at >= ? AND merchant_id IS NOT NULL "
                            + "GROUP BY 1 ORDER BY 2 DESC LIMIT 10",
                    rs -> {
                        merchants.computeIfAbsent(rs.getLong("merchant_id"), k -> new long[2])[1] = rs.getLong("n");
                    }, from.atStartOfDay());
        }

        List<Day> series = new ArrayList<>();
        perDay.forEach((d, v) -> series.add(new Day(d, v[0], v[1])));
        Map<Long, String> names = names(merchants.keySet());
        List<TopMerchant> top = merchants.entrySet().stream()
                .map(e -> new TopMerchant(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.assistant() + b.smartReports() * 10, a.assistant() + a.smartReports() * 10))
                .limit(10)
                .toList();
        BigDecimal cost = assistantUnitCost.multiply(BigDecimal.valueOf(questions))
                .add(smartReportUnitCost.multiply(BigDecimal.valueOf(reportCount - failedReports)))
                .setScale(2, RoundingMode.HALF_UP);
        return new Report(days, assistant, reports, questions, failedQuestions, avgMs, reportCount, failedReports, cost,
                costCurrency, assistantUnitCost, smartReportUnitCost, series, outcomes, topics, statuses, errors, top);
    }

    private List<Slice> slices(String sql, LocalDate from) {
        return jdbc.query(sql, (rs, i) -> new Slice(rs.getString("k"), rs.getLong("n")), from.atStartOfDay());
    }

    private boolean exists(String table) {
        Boolean found = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table);
        return Boolean.TRUE.equals(found);
    }

    private Map<Long, String> names(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new LinkedHashMap<>();
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        try {
            jdbc.query("SELECT id, trim(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) AS name, email FROM tbl_user WHERE id IN (" + in + ")",
                    rs -> {
                        String name = rs.getString("name");
                        out.put(rs.getLong("id"), name == null || name.isBlank() ? rs.getString("email") : name);
                    });
        } catch (RuntimeException ex) {
            log.debug("ai_usage_names_skipped reason={}", ex.getMessage());
        }
        return out;
    }
}
