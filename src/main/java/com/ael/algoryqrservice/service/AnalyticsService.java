package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductVisit;
import com.ael.algoryqrservice.model.MenuVisit;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final String MOBILE = "MOBILE";
    private static final String TABLET = "TABLET";
    private static final String DESKTOP = "DESKTOP";
    private static final int MAX_EVENTS = 50;
    private static final int TOP_LIMIT = 10;
    private static final int SAMPLE_JOURNEYS = 8;

    private final MenuVisitRepository menuVisitRepository;
    private final MenuProductVisitRepository menuProductVisitRepository;
    private final MenuAnalyticsSessionRepository sessionRepository;
    private final MenuAnalyticsEventRepository eventRepository;
    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    @Transactional
    public void recordEvents(Long menuId, AnalyticsDtos.AnalyticsEventsRequest request, String ipAddress, String userAgent) {
        Menu menu = requirePublicMenu(menuId);
        if (request.events().size() > MAX_EVENTS) {
            throw new BadRequestException("Tek istekte en fazla " + MAX_EVENTS + " olay gonderilebilir");
        }

        LocalDateTime now = LocalDateTime.now();
        String deviceType = resolveDeviceType(request.deviceType(), userAgent);
        String ipHash = hashIp(ipAddress);
        String ua = truncate(userAgent, 512);

        MenuAnalyticsSession session = sessionRepository.findById(request.sessionId())
                .orElseGet(() -> MenuAnalyticsSession.builder()
                        .id(request.sessionId())
                        .menuId(menu.getMenuId())
                        .startedAt(now)
                        .lastSeenAt(now)
                        .deviceType(deviceType)
                        .ipHash(ipHash)
                        .userAgent(ua)
                        .build());

        if (!session.getMenuId().equals(menuId)) {
            throw new BadRequestException("Session baska bir menuye ait");
        }

        long existingCount = eventRepository.countBySessionIdAndMenuId(request.sessionId(), menuId);
        if (existingCount > 500) {
            throw new BadRequestException("Session olay limiti asildi");
        }

        session.setLastSeenAt(now);
        session.setDeviceType(deviceType);
        if (ua != null && (session.getUserAgent() == null || session.getUserAgent().isBlank()
                || looksLikeProxyUserAgent(session.getUserAgent()))) {
            session.setUserAgent(ua);
        }
        if (ipHash != null && session.getIpHash() == null) {
            session.setIpHash(ipHash);
        }
        sessionRepository.save(session);

        List<MenuAnalyticsEvent> toSave = new ArrayList<>();
        int fallbackSeq = (int) existingCount;
        for (AnalyticsDtos.AnalyticsEventItemRequest item : request.events()) {
            validateEventItem(menuId, item);
            int sequence = item.sequence() != null ? item.sequence() : ++fallbackSeq;
            LocalDateTime occurredAt = item.occurredAt() != null ? item.occurredAt() : now;
            toSave.add(MenuAnalyticsEvent.builder()
                    .sessionId(request.sessionId())
                    .menuId(menuId)
                    .eventType(item.type())
                    .categoryId(item.categoryId())
                    .productId(item.productId())
                    .sequence(sequence)
                    .occurredAt(occurredAt)
                    .build());
        }
        eventRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuAnalyticsReportResponse getMenuReport(
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        Menu menu = requireOwnedMenu(menuId, ownerId);
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        long sessions = sessionRepository.countByMenuIdAndPeriod(menuId, fromDt, toDt);
        long menuOpens = eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                menuId, MenuAnalyticsEventType.MENU_OPEN, fromDt, toDt);
        long productViews = eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                menuId, MenuAnalyticsEventType.PRODUCT_VIEW, fromDt, toDt);
        long categoryViews = eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                menuId, MenuAnalyticsEventType.CATEGORY_VIEW, fromDt, toDt);
        Double avgProducts = eventRepository.avgProductsPerSession(menuId, fromDt, toDt);

        Map<LocalDate, Long> sessionsByDay = toDateCountMap(
                sessionRepository.countDailyByMenuIdAndPeriod(menuId, fromDt, toDt));
        Map<LocalDate, long[]> openProductByDay = new HashMap<>();
        for (Object[] row : eventRepository.countDailyOpenAndProductByMenuId(menuId, fromDt, toDt)) {
            LocalDate day = toLocalDate(row[0]);
            openProductByDay.put(day, new long[]{
                    row[1] == null ? 0L : ((Number) row[1]).longValue(),
                    row[2] == null ? 0L : ((Number) row[2]).longValue()
            });
        }

        List<AnalyticsDtos.DailyReportPoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            long[] op = openProductByDay.getOrDefault(cursor, new long[]{0L, 0L});
            daily.add(new AnalyticsDtos.DailyReportPoint(
                    cursor,
                    sessionsByDay.getOrDefault(cursor, 0L),
                    op[0],
                    op[1]
            ));
        }

        Map<Integer, Long> hourlyMap = new HashMap<>();
        for (Object[] row : eventRepository.countHourlyByMenuId(menuId, fromDt, toDt)) {
            hourlyMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        List<AnalyticsDtos.HourlyReportPoint> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new AnalyticsDtos.HourlyReportPoint(h, hourlyMap.getOrDefault(h, 0L)));
        }

        Map<String, Long> deviceCounts = toDeviceCountMap(
                sessionRepository.countByDeviceTypeAndPeriod(menuId, fromDt, toDt));
        List<AnalyticsDtos.NamedCount> devices = List.of(
                new AnalyticsDtos.NamedCount("Mobil", deviceCounts.getOrDefault(MOBILE, 0L)),
                new AnalyticsDtos.NamedCount("Tablet", deviceCounts.getOrDefault(TABLET, 0L)),
                new AnalyticsDtos.NamedCount("Masaustu", deviceCounts.getOrDefault(DESKTOP, 0L))
        );

        Map<Long, String> productNames = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId).stream()
                .collect(Collectors.toMap(MenuProduct::getProductId, MenuProduct::getName, (a, b) -> a));
        Map<Long, String> categoryNames = menuCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId).stream()
                .collect(Collectors.toMap(MenuCategory::getCategoryId, MenuCategory::getName, (a, b) -> a));

        List<AnalyticsDtos.TopProduct> topProducts = eventRepository.topProducts(menuId, fromDt, toDt).stream()
                .limit(TOP_LIMIT)
                .map(row -> {
                    Long productId = ((Number) row[0]).longValue();
                    return new AnalyticsDtos.TopProduct(
                            productId,
                            productNames.getOrDefault(productId, "Urun #" + productId),
                            ((Number) row[1]).longValue()
                    );
                })
                .toList();

        List<AnalyticsDtos.TopCategory> topCategories = eventRepository.topCategories(menuId, fromDt, toDt).stream()
                .limit(TOP_LIMIT)
                .map(row -> {
                    Long categoryId = ((Number) row[0]).longValue();
                    return new AnalyticsDtos.TopCategory(
                            categoryId,
                            categoryNames.getOrDefault(categoryId, "Kategori #" + categoryId),
                            ((Number) row[1]).longValue()
                    );
                })
                .toList();

        List<AnalyticsDtos.TreemapNode> tree = buildCategoryProductTree(
                eventRepository.productViewsByCategory(menuId, fromDt, toDt),
                productNames,
                categoryNames
        );

        List<MenuAnalyticsSession> recentSessions = sessionRepository
                .findRecentByMenuIdAndPeriod(menuId, fromDt, toDt).stream()
                .limit(SAMPLE_JOURNEYS)
                .toList();
        List<AnalyticsDtos.SampleJourney> journeys = buildSampleJourneys(
                recentSessions,
                productNames,
                categoryNames
        );

        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                menu.getMenuId(),
                menu.getBusinessName(),
                from,
                to,
                new AnalyticsDtos.ReportKpis(
                        sessions,
                        menuOpens,
                        productViews,
                        categoryViews,
                        avgProducts == null ? 0d : avgProducts
                ),
                daily,
                hourly,
                devices,
                topProducts,
                topCategories,
                tree,
                journeys,
                new AnalyticsDtos.FunnelCounts(menuOpens, categoryViews, productViews)
        );
    }

    @Transactional
    public void recordMenuVisit(Long menuId, String ipAddress, String userAgent) {
        menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted() && m.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        menuVisitRepository.save(MenuVisit.builder()
                .menuId(menuId)
                .ipAddress(ipAddress == null ? "0.0.0.0" : ipAddress)
                .userAgent(userAgent)
                .deviceType(detectDeviceType(userAgent))
                .visitedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void recordProductVisit(Long menuId, Long menuProductId, String ipAddress, String userAgent) {
        menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted() && m.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        menuProductRepository.findByProductIdAndDeletedFalse(menuProductId)
                .filter(p -> p.getMenuId().equals(menuId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));

        menuProductVisitRepository.save(MenuProductVisit.builder()
                .menuId(menuId)
                .menuProductId(menuProductId)
                .ipAddress(ipAddress == null ? "0.0.0.0" : ipAddress)
                .userAgent(userAgent)
                .deviceType(detectDeviceType(userAgent))
                .visitedAt(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.VisitPageResponse getMenuAnalytics(Long menuId, Long ownerId, LocalDate from, LocalDate to) {
        requireOwnedMenu(menuId, ownerId);
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

        return new AnalyticsDtos.VisitPageResponse(
                new AnalyticsDtos.VisitSummaryResponse(
                        total,
                        uniqueIps,
                        deviceCounts.getOrDefault(MOBILE, 0L),
                        deviceCounts.getOrDefault(TABLET, 0L),
                        deviceCounts.getOrDefault(DESKTOP, 0L)
                ),
                daily
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.VisitPageResponse getProductAnalytics(
            Long menuId,
            Long menuProductId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        requireOwnedMenu(menuId, ownerId);
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

        return new AnalyticsDtos.VisitPageResponse(
                new AnalyticsDtos.VisitSummaryResponse(
                        total,
                        uniqueIps,
                        deviceCounts.getOrDefault(MOBILE, 0L),
                        deviceCounts.getOrDefault(TABLET, 0L),
                        deviceCounts.getOrDefault(DESKTOP, 0L)
                ),
                daily
        );
    }

    public String extractIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String extractUserAgent(HttpServletRequest request) {
        String clientUa = request.getHeader("X-Client-User-Agent");
        if (clientUa != null && !clientUa.isBlank() && !looksLikeProxyUserAgent(clientUa)) {
            return clientUa.trim();
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && !ua.isBlank() && !looksLikeProxyUserAgent(ua)) {
            return ua.trim();
        }
        return ua;
    }

    private String resolveDeviceType(String requested, String userAgent) {
        String normalized = normalizeDeviceType(requested);
        if (normalized != null) {
            return normalized;
        }
        return detectDeviceType(userAgent);
    }

    private String normalizeDeviceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (MOBILE.equals(normalized) || TABLET.equals(normalized) || DESKTOP.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private boolean looksLikeProxyUserAgent(String userAgent) {
        String ua = userAgent.toLowerCase();
        return ua.startsWith("axios/")
                || ua.startsWith("node")
                || ua.startsWith("next.js")
                || ua.startsWith("undici")
                || ua.contains("node-fetch");
    }

    private void validateEventItem(Long menuId, AnalyticsDtos.AnalyticsEventItemRequest item) {
        if (item.type() == MenuAnalyticsEventType.CATEGORY_VIEW) {
            if (item.categoryId() == null) {
                throw new BadRequestException("CATEGORY_VIEW icin categoryId zorunludur");
            }
            menuCategoryRepository.findByCategoryIdAndDeletedFalse(item.categoryId())
                    .filter(c -> c.getMenuId().equals(menuId))
                    .orElseThrow(() -> new BadRequestException("Kategori bulunamadi"));
        }
        if (item.type() == MenuAnalyticsEventType.PRODUCT_VIEW) {
            if (item.productId() == null) {
                throw new BadRequestException("PRODUCT_VIEW icin productId zorunludur");
            }
            menuProductRepository.findByProductIdAndDeletedFalse(item.productId())
                    .filter(p -> p.getMenuId().equals(menuId))
                    .orElseThrow(() -> new BadRequestException("Urun bulunamadi"));
        }
    }

    private Menu requirePublicMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted() && m.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.isPublicAccessEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Menu herkese acik degil");
        }
        return menu;
    }

    private Menu requireOwnedMenu(Long menuId, Long ownerId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!menu.getUserId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private List<AnalyticsDtos.TreemapNode> buildCategoryProductTree(
            List<Object[]> rows,
            Map<Long, String> productNames,
            Map<Long, String> categoryNames
    ) {
        Map<String, List<AnalyticsDtos.TreemapNode>> byCategory = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long categoryId = row[0] == null ? null : ((Number) row[0]).longValue();
            Long productId = ((Number) row[1]).longValue();
            long views = ((Number) row[2]).longValue();
            String categoryName = categoryId == null
                    ? "Genel"
                    : categoryNames.getOrDefault(categoryId, "Kategori #" + categoryId);
            byCategory.computeIfAbsent(categoryName, ignored -> new ArrayList<>())
                    .add(new AnalyticsDtos.TreemapNode(
                            productNames.getOrDefault(productId, "Urun #" + productId),
                            views,
                            List.of()
                    ));
        }
        return byCategory.entrySet().stream()
                .map(entry -> {
                    long size = entry.getValue().stream().mapToLong(AnalyticsDtos.TreemapNode::size).sum();
                    return new AnalyticsDtos.TreemapNode(entry.getKey(), size, entry.getValue());
                })
                .sorted(Comparator.comparingLong(AnalyticsDtos.TreemapNode::size).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    private List<AnalyticsDtos.SampleJourney> buildSampleJourneys(
            List<MenuAnalyticsSession> sessions,
            Map<Long, String> productNames,
            Map<Long, String> categoryNames
    ) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = sessions.stream().map(MenuAnalyticsSession::getId).toList();
        Map<UUID, List<MenuAnalyticsEvent>> eventsBySession = eventRepository
                .findBySessionIdInOrderBySessionIdAscSequenceAsc(ids).stream()
                .collect(Collectors.groupingBy(MenuAnalyticsEvent::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<AnalyticsDtos.SampleJourney> journeys = new ArrayList<>();
        for (MenuAnalyticsSession session : sessions) {
            List<AnalyticsDtos.JourneyStep> steps = eventsBySession
                    .getOrDefault(session.getId(), List.of())
                    .stream()
                    .limit(20)
                    .map(event -> new AnalyticsDtos.JourneyStep(
                            event.getEventType().name(),
                            resolveStepName(event, productNames, categoryNames),
                            event.getOccurredAt()
                    ))
                    .toList();
            journeys.add(new AnalyticsDtos.SampleJourney(session.getId(), session.getStartedAt(), steps));
        }
        return journeys;
    }

    private String resolveStepName(
            MenuAnalyticsEvent event,
            Map<Long, String> productNames,
            Map<Long, String> categoryNames
    ) {
        return switch (event.getEventType()) {
            case MENU_OPEN -> "Menu acildi";
            case CATEGORY_VIEW -> categoryNames.getOrDefault(
                    event.getCategoryId(),
                    "Kategori #" + event.getCategoryId()
            );
            case PRODUCT_VIEW -> productNames.getOrDefault(
                    event.getProductId(),
                    "Urun #" + event.getProductId()
            );
        };
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

    private Map<LocalDate, Long> toDateCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<AnalyticsDtos.DailyVisitResponse> toDailyList(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AnalyticsDtos.DailyVisitResponse(
                        toLocalDate(row[0]),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }

    private String hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(ipAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(ipAddress.hashCode());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
