package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MenuProductVisit;
import com.ael.algoryqrservice.model.MenuVisit;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductVisitRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuVisitRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final String MOBILE = "MOBILE";
    private static final String TABLET = "TABLET";
    private static final String DESKTOP = "DESKTOP";

    private final MenuVisitRepository menuVisitRepository;
    private final MenuProductVisitRepository menuProductVisitRepository;
    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;

    @Transactional
    public void recordMenuVisit(Long menuId, String ipAddress, String userAgent) {
        menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted() && m.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        MenuVisit visit = MenuVisit.builder()
                .menuId(menuId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(detectDeviceType(userAgent))
                .visitedAt(LocalDateTime.now())
                .build();

        menuVisitRepository.save(visit);
    }

    @Transactional
    public void recordProductVisit(Long menuId, Long menuProductId, String ipAddress, String userAgent) {
        menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted() && m.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        menuProductRepository.findByProductIdAndDeletedFalse(menuProductId)
                .filter(p -> p.getMenuId().equals(menuId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));

        MenuProductVisit visit = MenuProductVisit.builder()
                .menuId(menuId)
                .menuProductId(menuProductId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(detectDeviceType(userAgent))
                .visitedAt(LocalDateTime.now())
                .build();

        menuProductVisitRepository.save(visit);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.VisitPageResponse getMenuAnalytics(Long menuId, Long ownerId, LocalDate from, LocalDate to) {
        var menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        if (!menu.getUserId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        long total = menuVisitRepository.countByMenuIdAndPeriod(menuId, fromDt, toDt);
        long uniqueIps = menuVisitRepository.countDistinctIpByMenuIdAndPeriod(menuId, fromDt, toDt);
        Map<String, Long> deviceCounts = toDeviceCountMap(
                menuVisitRepository.countByDeviceTypeAndPeriod(menuId, fromDt, toDt)
        );
        List<AnalyticsDtos.DailyVisitResponse> daily = toDailyList(
                menuVisitRepository.countDailyByMenuIdAndPeriod(menuId, fromDt, toDt)
        );

        AnalyticsDtos.VisitSummaryResponse summary = new AnalyticsDtos.VisitSummaryResponse(
                total,
                uniqueIps,
                deviceCounts.getOrDefault(MOBILE, 0L),
                deviceCounts.getOrDefault(TABLET, 0L),
                deviceCounts.getOrDefault(DESKTOP, 0L)
        );

        return new AnalyticsDtos.VisitPageResponse(summary, daily);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.VisitPageResponse getProductAnalytics(Long menuId, Long menuProductId, Long ownerId, LocalDate from, LocalDate to) {
        var menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        if (!menu.getUserId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }

        menuProductRepository.findByProductIdAndDeletedFalse(menuProductId)
                .filter(p -> p.getMenuId().equals(menuId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        long total = menuProductVisitRepository.countByMenuProductIdAndPeriod(menuProductId, fromDt, toDt);
        long uniqueIps = menuProductVisitRepository.countDistinctIpByMenuProductIdAndPeriod(menuProductId, fromDt, toDt);
        Map<String, Long> deviceCounts = toDeviceCountMap(
                menuProductVisitRepository.countByDeviceTypeAndPeriod(menuProductId, fromDt, toDt)
        );
        List<AnalyticsDtos.DailyVisitResponse> daily = toDailyList(
                menuProductVisitRepository.countDailyByMenuProductIdAndPeriod(menuProductId, fromDt, toDt)
        );

        AnalyticsDtos.VisitSummaryResponse summary = new AnalyticsDtos.VisitSummaryResponse(
                total,
                uniqueIps,
                deviceCounts.getOrDefault(MOBILE, 0L),
                deviceCounts.getOrDefault(TABLET, 0L),
                deviceCounts.getOrDefault(DESKTOP, 0L)
        );

        return new AnalyticsDtos.VisitPageResponse(summary, daily);
    }

    public String extractIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String detectDeviceType(String userAgent) {
        if (userAgent == null) {
            return DESKTOP;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return TABLET;
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipod")) {
            return MOBILE;
        }
        return DESKTOP;
    }

    private Map<String, Long> toDeviceCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<AnalyticsDtos.DailyVisitResponse> toDailyList(List<Object[]> rows) {
        return rows.stream()
                .map(row -> {
                    LocalDate date;
                    if (row[0] instanceof java.sql.Date sqlDate) {
                        date = sqlDate.toLocalDate();
                    } else if (row[0] instanceof LocalDate ld) {
                        date = ld;
                    } else {
                        date = LocalDate.parse(row[0].toString());
                    }
                    long count = ((Number) row[1]).longValue();
                    return new AnalyticsDtos.DailyVisitResponse(date, count);
                })
                .toList();
    }
}
