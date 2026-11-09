package com.ael.algoryqrservice.qrservice.qr.repository;

import com.ael.algoryqrservice.qrservice.qr.dto.QrDailyScan;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class QrScanStore {

    private final NamedParameterJdbcTemplate jdbc;

    public void increment(Long qrId, LocalDate day) {
        jdbc.update("""
                INSERT INTO qr_scan_daily (qr_id, scan_date, scan_count)
                VALUES (:qrId, :day, 1)
                ON CONFLICT (qr_id, scan_date)
                DO UPDATE SET scan_count = qr_scan_daily.scan_count + 1
                """, Map.of("qrId", qrId, "day", Date.valueOf(day)));
    }

    public Map<Long, Long> totals(Collection<Long> qrIds) {
        Map<Long, Long> totals = new HashMap<>();
        if (qrIds.isEmpty()) {
            return totals;
        }
        jdbc.query("""
                SELECT qr_id, SUM(scan_count) AS total
                FROM qr_scan_daily
                WHERE qr_id IN (:qrIds)
                GROUP BY qr_id
                """, Map.of("qrIds", qrIds),
                rs -> { totals.put(rs.getLong("qr_id"), rs.getLong("total")); });
        return totals;
    }

    public Map<Long, List<QrDailyScan>> dailySince(Collection<Long> qrIds, LocalDate from) {
        Map<Long, List<QrDailyScan>> daily = new HashMap<>();
        if (qrIds.isEmpty()) {
            return daily;
        }
        jdbc.query("""
                SELECT qr_id, scan_date, scan_count
                FROM qr_scan_daily
                WHERE qr_id IN (:qrIds) AND scan_date >= :from
                ORDER BY scan_date
                """, Map.of("qrIds", qrIds, "from", Date.valueOf(from)),
                rs -> {
                    daily.computeIfAbsent(rs.getLong("qr_id"), id -> new ArrayList<>())
                            .add(new QrDailyScan(rs.getDate("scan_date").toLocalDate(), rs.getLong("scan_count")));
                });
        return daily;
    }
}
