package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductVisit;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.MenuVisit;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductVisitRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.repository.MenuVisitRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final int UNSOLD_LIMIT = 8;

    private static final int WAITER_TOP_PRODUCTS = 5;

    private final MenuVisitRepository menuVisitRepository;
    private final MenuProductVisitRepository menuProductVisitRepository;
    private final MenuAnalyticsSessionRepository sessionRepository;
    private final MenuAnalyticsEventRepository eventRepository;
    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final MenuFeedbackService menuFeedbackService;
    private final MenuOrderRepository menuOrderRepository;
    private final MenuWaiterRepository menuWaiterRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final TableBillRepository tableBillRepository;
    private final MenuFixedExpenseService menuFixedExpenseService;
    private final BranchService branchService;
    private final BranchRepository branchRepository;

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
                    .servesPeople(item.servesPeople())
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
        return buildVisitReport(resolveMenuScope(menuId, ownerId), from, to);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuAnalyticsReportResponse getBranchReport(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        return buildVisitReport(resolveBranchScope(branchId, menuId, ownerId), from, to);
    }

    private AnalyticsDtos.MenuAnalyticsReportResponse buildVisitReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        if (scope.menuIds().isEmpty()) {
            return emptyVisitReport(scope, from, to);
        }
        Collection<Long> menuIds = scope.menuIds();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        long sessions = sessionRepository.countByMenuIdInAndPeriod(menuIds, fromDt, toDt);
        long menuOpens = eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                menuIds, MenuAnalyticsEventType.MENU_OPEN, fromDt, toDt);
        long productViews = eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                menuIds, MenuAnalyticsEventType.PRODUCT_VIEW, fromDt, toDt);
        long categoryViews = eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                menuIds, MenuAnalyticsEventType.CATEGORY_VIEW, fromDt, toDt);
        Double avgProducts = eventRepository.avgProductsPerSessionByMenuIds(menuIds, fromDt, toDt);

        Map<LocalDate, Long> sessionsByDay = toDateCountMap(
                sessionRepository.countDailyByMenuIdInAndPeriod(menuIds, fromDt, toDt));
        Map<LocalDate, long[]> openProductByDay = new HashMap<>();
        for (Object[] row : eventRepository.countDailyOpenAndProductByMenuIdIn(menuIds, fromDt, toDt)) {
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
        for (Object[] row : eventRepository.countHourlyByMenuIdIn(menuIds, fromDt, toDt)) {
            hourlyMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        List<AnalyticsDtos.HourlyReportPoint> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new AnalyticsDtos.HourlyReportPoint(h, hourlyMap.getOrDefault(h, 0L)));
        }

        Map<String, Long> deviceCounts = toDeviceCountMap(
                sessionRepository.countByDeviceTypeAndPeriodForMenuIds(menuIds, fromDt, toDt));
        List<AnalyticsDtos.NamedCount> devices = List.of(
                new AnalyticsDtos.NamedCount("Mobil", deviceCounts.getOrDefault(MOBILE, 0L)),
                new AnalyticsDtos.NamedCount("Tablet", deviceCounts.getOrDefault(TABLET, 0L)),
                new AnalyticsDtos.NamedCount("Masaustu", deviceCounts.getOrDefault(DESKTOP, 0L))
        );

        List<MenuProduct> catalog = menuProductRepository
                .findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuIds);
        Map<Long, String> productNames = labeledProductNames(scope, catalog);
        Map<Long, String> categoryNames = menuSubCategoryRepository
                .findByIdInAndDeletedFalse(
                        catalog.stream().map(MenuProduct::getSubCategoryId).filter(Objects::nonNull).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, MenuSubCategory::getName, (a, b) -> a));

        List<AnalyticsDtos.TopProduct> topProducts = eventRepository.topProductsByMenuIds(menuIds, fromDt, toDt).stream()
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

        List<AnalyticsDtos.TopCategory> topCategories = eventRepository.topCategoriesByMenuIds(menuIds, fromDt, toDt).stream()
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
                eventRepository.productViewsByCategoryForMenuIds(menuIds, fromDt, toDt),
                productNames,
                categoryNames
        );

        List<MenuAnalyticsSession> recentSessions = sessionRepository
                .findRecentByMenuIdInAndPeriod(menuIds, fromDt, toDt).stream()
                .limit(SAMPLE_JOURNEYS)
                .toList();
        List<AnalyticsDtos.SampleJourney> journeys = buildSampleJourneys(
                recentSessions,
                productNames,
                categoryNames
        );

        AnalyticsDtos.ReportFeedback feedback = menuFeedbackService.buildReportFeedback(menuIds, from, to);

        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
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
                new AnalyticsDtos.FunnelCounts(menuOpens, categoryViews, productViews),
                feedback
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuRevenueReportResponse getMenuRevenueReport(
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        return buildRevenueReport(resolveMenuScope(menuId, ownerId), from, to);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuRevenueReportResponse getBranchRevenueReport(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        return buildRevenueReport(resolveBranchScope(branchId, menuId, ownerId), from, to);
    }

    private AnalyticsDtos.MenuRevenueReportResponse buildRevenueReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        if (scope.menuIds().isEmpty()) {
            return emptyRevenueReport(scope, from, to);
        }
        Collection<Long> menuIds = scope.menuIds();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<BillPayment> payments = billPaymentRepository.findByMenuIdInAndPaidAtBetween(menuIds, fromDt, toDt);

        List<MenuProduct> catalogProducts = menuProductRepository
                .findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuIds);
        Map<Long, MenuProduct> productsById = catalogProducts.stream()
                .collect(Collectors.toMap(MenuProduct::getProductId, p -> p, (a, b) -> a));
        Map<Long, String> labeledNames = labeledProductNames(scope, catalogProducts);
        Map<Long, String> categoryNames = menuSubCategoryRepository
                .findByIdInAndDeletedFalse(
                        catalogProducts.stream().map(MenuProduct::getSubCategoryId).filter(Objects::nonNull).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, MenuSubCategory::getName, (a, b) -> a));
        List<MenuWaiter> waiters = loadWaiters(scope);
        Map<Long, MenuWaiter> waitersById = waiters.stream()
                .collect(Collectors.toMap(MenuWaiter::getId, w -> w, (a, b) -> a));
        Map<Long, String> waiterNames = labeledWaiterNames(scope, waiters);

        BigDecimal cashRevenue = BigDecimal.ZERO;
        BigDecimal cardRevenue = BigDecimal.ZERO;
        BigDecimal tipRevenue = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long itemCount = 0L;
        String currency = "TRY";
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        Map<LocalDate, Long> ordersByDay = new HashMap<>();
        Map<Integer, BigDecimal> revenueByHour = new HashMap<>();
        Map<Integer, Long> ordersByHour = new HashMap<>();
        Map<Long, AnalyticsDtos.RevenueProduct> products = new LinkedHashMap<>();
        Map<Long, AnalyticsDtos.RevenueCategory> categories = new LinkedHashMap<>();
        Map<Long, PersonnelPaymentAgg> personnelStats = new LinkedHashMap<>();
        Set<Long> distinctBills = new java.util.HashSet<>();

        for (BillPayment payment : payments) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (payment.getBill() != null && payment.getBill().getCurrency() != null
                    && !payment.getBill().getCurrency().isBlank()) {
                currency = payment.getBill().getCurrency();
            }

            if (payment.isTip()) {
                tipRevenue = tipRevenue.add(amount);
            } else if (payment.getPaymentMethod() == TableBillPaymentMethod.CASH) {
                cashRevenue = cashRevenue.add(amount);
            } else if (payment.getPaymentMethod() == TableBillPaymentMethod.CARD) {
                cardRevenue = cardRevenue.add(amount);
            }
            totalRevenue = totalRevenue.add(amount);

            LocalDate day = payment.getPaidAt() != null ? payment.getPaidAt().toLocalDate() : from;
            int hour = payment.getPaidAt() != null ? payment.getPaidAt().getHour() : 0;
            revenueByDay.merge(day, amount, BigDecimal::add);
            revenueByHour.merge(hour, amount, BigDecimal::add);

            if (payment.getBill() != null) {
                distinctBills.add(payment.getBill().getId());
            }

            if (!payment.isTip()) {
                ordersByDay.merge(day, 0L, (a, b) -> a);
                ordersByHour.merge(hour, 0L, (a, b) -> a);
            }

            TableBillItem billItem = payment.getBillItem();
            if (billItem != null && !payment.isTip()) {
                long qty = payment.getQuantityPaid();
                itemCount += qty;

                Long productId = billItem.getProductId();
                AnalyticsDtos.RevenueProduct existingProduct = products.get(productId);
                String productName = labeledNames.getOrDefault(
                        productId,
                        billItem.getProductName() != null ? billItem.getProductName() : "Ürün #" + productId
                );
                products.put(productId, new AnalyticsDtos.RevenueProduct(
                        productId,
                        productName,
                        (existingProduct == null ? 0L : existingProduct.quantity()) + qty,
                        (existingProduct == null ? BigDecimal.ZERO : existingProduct.revenue()).add(amount)
                ));

                MenuProduct catalog = productsById.get(productId);
                Long categoryId = catalog == null ? 0L : catalog.getSubCategoryId();
                String categoryName = categoryId == null || categoryId == 0L
                        ? "Diğer"
                        : categoryNames.getOrDefault(categoryId, "Kategori #" + categoryId);
                Long categoryKey = categoryId == null ? 0L : categoryId;
                AnalyticsDtos.RevenueCategory existingCategory = categories.get(categoryKey);
                categories.put(categoryKey, new AnalyticsDtos.RevenueCategory(
                        categoryKey == 0L ? null : categoryKey,
                        categoryName,
                        (existingCategory == null ? 0L : existingCategory.quantity()) + qty,
                        (existingCategory == null ? BigDecimal.ZERO : existingCategory.revenue()).add(amount)
                ));
            }

            if (payment.getWaiterId() != null) {
                PersonnelPaymentAgg stats = personnelStats.computeIfAbsent(
                        payment.getWaiterId(),
                        ignored -> new PersonnelPaymentAgg()
                );
                stats.add(payment);
            }
        }

        Map<LocalDate, java.util.Set<Long>> billsByDay = new HashMap<>();
        for (BillPayment payment : payments) {
            if (payment.isTip() || payment.getPaidAt() == null || payment.getBill() == null) {
                continue;
            }
            LocalDate day = payment.getPaidAt().toLocalDate();
            billsByDay.computeIfAbsent(day, ignored -> new java.util.HashSet<>())
                    .add(payment.getBill().getId());
        }
        for (Map.Entry<LocalDate, java.util.Set<Long>> entry : billsByDay.entrySet()) {
            ordersByDay.put(entry.getKey(), (long) entry.getValue().size());
        }

        List<AnalyticsDtos.DailyRevenuePoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyRevenuePoint(
                    cursor,
                    revenueByDay.getOrDefault(cursor, BigDecimal.ZERO),
                    ordersByDay.getOrDefault(cursor, 0L)
            ));
        }

        List<AnalyticsDtos.RevenueProduct> productRows = products.values().stream()
                .sorted(Comparator.comparing(AnalyticsDtos.RevenueProduct::revenue).reversed())
                .toList();
        List<AnalyticsDtos.RevenueCategory> categoryRows = categories.values().stream()
                .sorted(Comparator.comparing(AnalyticsDtos.RevenueCategory::revenue).reversed())
                .toList();

        Map<Integer, Long> paymentsByHour = new HashMap<>();
        for (BillPayment payment : payments) {
            if (payment.isTip() || payment.getPaidAt() == null) {
                continue;
            }
            int hour = payment.getPaidAt().getHour();
            paymentsByHour.merge(hour, 1L, Long::sum);
        }

        List<AnalyticsDtos.HourlyRevenuePoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new AnalyticsDtos.HourlyRevenuePoint(
                    hour,
                    revenueByHour.getOrDefault(hour, BigDecimal.ZERO),
                    paymentsByHour.getOrDefault(hour, 0L)
            ));
        }

        long orderCount = distinctBills.size();
        BigDecimal avgOrderValue = orderCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.subtract(tipRevenue).divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        long dayCount = from.until(to).getDays() + 1L;
        BigDecimal dailyFixedTotal = menuFixedExpenseService.totalDailyActiveAmount(menuIds);
        BigDecimal fixedExpenseTotal = dailyFixedTotal.multiply(BigDecimal.valueOf(dayCount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossRevenue = totalRevenue;
        BigDecimal netRevenue = grossRevenue.subtract(fixedExpenseTotal).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        List<AnalyticsDtos.RevenuePersonnelRow> personnelRows = new ArrayList<>();
        for (Map.Entry<Long, PersonnelPaymentAgg> entry : personnelStats.entrySet()) {
            MenuWaiter waiter = waitersById.get(entry.getKey());
            PersonnelPaymentAgg stats = entry.getValue();
            personnelRows.add(new AnalyticsDtos.RevenuePersonnelRow(
                    entry.getKey(),
                    waiterNames.getOrDefault(
                            entry.getKey(),
                            waiter != null ? waiter.getDisplayName() : "Personel #" + entry.getKey()
                    ),
                    stats.total,
                    stats.cash,
                    stats.card,
                    stats.tip,
                    waiter == null || waiter.isActive()
            ));
        }
        personnelRows.sort(Comparator.comparing(AnalyticsDtos.RevenuePersonnelRow::revenue).reversed());

        AnalyticsDtos.RevenuePaymentBreakdown paymentBreakdown = new AnalyticsDtos.RevenuePaymentBreakdown(
                cashRevenue.setScale(2, RoundingMode.HALF_UP),
                cardRevenue.setScale(2, RoundingMode.HALF_UP),
                tipRevenue.setScale(2, RoundingMode.HALF_UP),
                grossRevenue.setScale(2, RoundingMode.HALF_UP),
                fixedExpenseTotal,
                netRevenue,
                currency
        );

        return new AnalyticsDtos.MenuRevenueReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
                from,
                to,
                new AnalyticsDtos.RevenueKpis(totalRevenue, orderCount, itemCount, avgOrderValue, currency),
                daily,
                productRows,
                categoryRows,
                buildRevenueSpotlight(productRows),
                hourly,
                buildUnsoldCatalog(productsById, products.keySet()),
                paymentBreakdown,
                personnelRows
        );
    }

    private static final class PersonnelPaymentAgg {
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal card = BigDecimal.ZERO;
        private BigDecimal tip = BigDecimal.ZERO;

        private void add(BillPayment payment) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            total = total.add(amount);
            if (payment.isTip()) {
                tip = tip.add(amount);
            } else if (payment.getPaymentMethod() == TableBillPaymentMethod.CASH) {
                cash = cash.add(amount);
            } else if (payment.getPaymentMethod() == TableBillPaymentMethod.CARD) {
                card = card.add(amount);
            }
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuWaiterPerformanceReportResponse getMenuWaiterPerformanceReport(
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        return buildWaiterPerformanceReport(resolveMenuScope(menuId, ownerId), from, to);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MenuWaiterPerformanceReportResponse getBranchWaiterPerformanceReport(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    ) {
        return buildWaiterPerformanceReport(resolveBranchScope(branchId, menuId, ownerId), from, to);
    }

    private AnalyticsDtos.MenuWaiterPerformanceReportResponse buildWaiterPerformanceReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        if (scope.menuIds().isEmpty()) {
            return emptyWaiterReport(scope, from, to);
        }
        Collection<Long> menuIds = scope.menuIds();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<MenuOrder> orders = menuOrderRepository
                .findByMenuIdInAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
                        menuIds,
                        MenuOrderStatus.CONFIRMED,
                        fromDt,
                        toDt
                );

        List<TableBill> closedBills = tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetween(
                menuIds,
                TableBillStatus.CLOSED,
                fromDt,
                toDt
        );

        List<MenuWaiter> waiters = loadWaiters(scope);
        Map<Long, String> waiterNames = labeledWaiterNames(scope, waiters);
        Map<Long, MenuWaiter> waitersById = waiters.stream()
                .collect(Collectors.toMap(MenuWaiter::getId, w -> w, (a, b) -> a));

        Map<Long, WaiterPerformanceAgg> statsByWaiterId = new LinkedHashMap<>();
        for (MenuWaiter waiter : waiters) {
            statsByWaiterId.put(waiter.getId(), new WaiterPerformanceAgg());
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalItemCount = 0L;
        BigDecimal totalCommission = BigDecimal.ZERO;
        long assignedOrderCount = 0L;
        long unassignedOrderCount = 0L;
        long billsClosedCount = 0L;
        WaiterPerformanceAgg unassignedStats = new WaiterPerformanceAgg();
        String currency = "TRY";
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        Map<LocalDate, Long> ordersByDay = new HashMap<>();
        Map<Integer, BigDecimal> revenueByHour = new HashMap<>();
        Map<Integer, Long> ordersByHour = new HashMap<>();
        Map<Long, ProductAgg> aggregateProducts = new LinkedHashMap<>();

        for (MenuOrder order : orders) {
            BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(orderTotal);
            if (order.getCurrency() != null && !order.getCurrency().isBlank()) {
                currency = order.getCurrency();
            }

            BigDecimal orderCommission = order.getCommissionAmount() != null
                    ? order.getCommissionAmount()
                    : BigDecimal.ZERO;
            totalCommission = totalCommission.add(orderCommission);

            LocalDate day = order.getConfirmedAt() != null ? order.getConfirmedAt().toLocalDate() : from;
            int hour = order.getConfirmedAt() != null ? order.getConfirmedAt().getHour() : 0;
            revenueByDay.merge(day, orderTotal, BigDecimal::add);
            ordersByDay.merge(day, 1L, Long::sum);
            revenueByHour.merge(hour, orderTotal, BigDecimal::add);
            ordersByHour.merge(hour, 1L, Long::sum);

            long orderItemCount = 0L;
            if (order.getItems() != null) {
                for (MenuOrderItem item : order.getItems()) {
                    int qty = item.getQuantity();
                    orderItemCount += qty;
                    totalItemCount += qty;

                    Long productId = item.getProductId();
                    String productName = item.getProductName() != null
                            ? item.getProductName()
                            : "Ürün #" + productId;
                    BigDecimal lineTotal = item.getLineTotal() != null
                            ? item.getLineTotal()
                            : BigDecimal.ZERO;
                    aggregateProducts.computeIfAbsent(productId, ignored -> new ProductAgg(productId, productName))
                            .add(qty, lineTotal);
                }
            }

            Long waiterId = order.getWaiterId();
            if (waiterId == null) {
                unassignedOrderCount++;
                unassignedStats.add(orderTotal, orderItemCount, orderCommission, order.getItems());
                continue;
            }

            assignedOrderCount++;
            WaiterPerformanceAgg stats = statsByWaiterId.computeIfAbsent(waiterId, ignored -> new WaiterPerformanceAgg());
            stats.add(orderTotal, orderItemCount, orderCommission, order.getItems());
        }

        for (TableBill bill : closedBills) {
            billsClosedCount++;
            Long closedByWaiterId = bill.getClosedByWaiterId();
            if (closedByWaiterId == null) {
                continue;
            }
            WaiterPerformanceAgg stats = statsByWaiterId.computeIfAbsent(closedByWaiterId, ignored -> new WaiterPerformanceAgg());
            stats.billsClosedCount++;
        }

        long activeWaiterCount = waiters.stream().filter(MenuWaiter::isActive).count();
        long totalOrders = orders.size();
        List<AnalyticsDtos.WaiterPerformanceRow> rows = new ArrayList<>();

        for (MenuWaiter waiter : waiters) {
            WaiterPerformanceAgg stats = statsByWaiterId.getOrDefault(waiter.getId(), new WaiterPerformanceAgg());
            rows.add(buildWaiterPerformanceRow(
                    waiter.getId(),
                    waiterNames.getOrDefault(waiter.getId(), waiter.getDisplayName()),
                    waiter.isActive(),
                    stats,
                    totalRevenue,
                    totalOrders,
                    totalItemCount
            ));
        }

        for (Map.Entry<Long, WaiterPerformanceAgg> entry : statsByWaiterId.entrySet()) {
            if (waitersById.containsKey(entry.getKey())) {
                continue;
            }
            MenuWaiter missing = menuWaiterRepository.findById(entry.getKey()).orElse(null);
            String name = waiterNames.getOrDefault(
                    entry.getKey(),
                    missing != null ? missing.getDisplayName() : "Personel #" + entry.getKey()
            );
            boolean active = missing != null && missing.isActive();
            rows.add(buildWaiterPerformanceRow(
                    entry.getKey(),
                    name,
                    active,
                    entry.getValue(),
                    totalRevenue,
                    totalOrders,
                    totalItemCount
            ));
        }

        if (unassignedOrderCount > 0) {
            rows.add(buildWaiterPerformanceRow(
                    null,
                    "Atanmamış",
                    false,
                    unassignedStats,
                    totalRevenue,
                    totalOrders,
                    totalItemCount
            ));
        }

        rows.sort(Comparator
                .comparing(AnalyticsDtos.WaiterPerformanceRow::revenue)
                .reversed()
                .thenComparing(AnalyticsDtos.WaiterPerformanceRow::displayName));

        List<AnalyticsDtos.DailyRevenuePoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyRevenuePoint(
                    cursor,
                    revenueByDay.getOrDefault(cursor, BigDecimal.ZERO),
                    ordersByDay.getOrDefault(cursor, 0L)
            ));
        }

        List<AnalyticsDtos.HourlyRevenuePoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new AnalyticsDtos.HourlyRevenuePoint(
                    hour,
                    revenueByHour.getOrDefault(hour, BigDecimal.ZERO),
                    ordersByHour.getOrDefault(hour, 0L)
            ));
        }

        List<AnalyticsDtos.WaiterPerformanceProduct> products = aggregateProducts.values().stream()
                .map(ProductAgg::toDto)
                .sorted(Comparator.comparing(AnalyticsDtos.WaiterPerformanceProduct::quantity).reversed())
                .limit(TOP_LIMIT)
                .toList();

        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
                from,
                to,
                new AnalyticsDtos.WaiterPerformanceKpis(
                        activeWaiterCount,
                        assignedOrderCount,
                        unassignedOrderCount,
                        totalRevenue,
                        totalItemCount,
                        totalCommission,
                        billsClosedCount,
                        currency
                ),
                rows,
                daily,
                hourly,
                products
        );
    }

    private AnalyticsDtos.WaiterPerformanceRow buildWaiterPerformanceRow(
            Long waiterId,
            String displayName,
            boolean active,
            WaiterPerformanceAgg stats,
            BigDecimal totalRevenue,
            long totalOrders,
            long totalItemCount
    ) {
        BigDecimal avgOrderValue = stats.orderCount == 0
                ? BigDecimal.ZERO
                : stats.revenue.divide(BigDecimal.valueOf(stats.orderCount), 2, RoundingMode.HALF_UP);
        double revenueSharePercent = totalRevenue.compareTo(BigDecimal.ZERO) == 0
                ? 0d
                : stats.revenue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                        .doubleValue();
        double orderSharePercent = totalOrders == 0
                ? 0d
                : BigDecimal.valueOf(stats.orderCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                        .doubleValue();
        double itemSharePercent = totalItemCount == 0
                ? 0d
                : BigDecimal.valueOf(stats.itemCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalItemCount), 2, RoundingMode.HALF_UP)
                        .doubleValue();
        List<AnalyticsDtos.WaiterPerformanceProduct> topProducts = stats.products.values().stream()
                .map(ProductAgg::toDto)
                .sorted(Comparator.comparing(AnalyticsDtos.WaiterPerformanceProduct::quantity).reversed())
                .limit(WAITER_TOP_PRODUCTS)
                .toList();
        return new AnalyticsDtos.WaiterPerformanceRow(
                waiterId,
                displayName,
                stats.orderCount,
                stats.itemCount,
                stats.revenue,
                stats.commission,
                stats.billsClosedCount,
                avgOrderValue,
                revenueSharePercent,
                orderSharePercent,
                itemSharePercent,
                active,
                topProducts
        );
    }

    private static final class WaiterPerformanceAgg {
        private long orderCount;
        private long itemCount;
        private long billsClosedCount;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private final Map<Long, ProductAgg> products = new LinkedHashMap<>();

        private void add(
                BigDecimal amount,
                long items,
                BigDecimal orderCommission,
                List<MenuOrderItem> orderItems
        ) {
            orderCount++;
            itemCount += items;
            revenue = revenue.add(amount);
            commission = commission.add(orderCommission);
            if (orderItems == null) {
                return;
            }
            for (MenuOrderItem item : orderItems) {
                int qty = item.getQuantity();
                Long productId = item.getProductId();
                String productName = item.getProductName() != null
                        ? item.getProductName()
                        : "Ürün #" + productId;
                BigDecimal lineTotal = item.getLineTotal() != null
                        ? item.getLineTotal()
                        : BigDecimal.ZERO;
                products.computeIfAbsent(productId, ignored -> new ProductAgg(productId, productName))
                        .add(qty, lineTotal);
            }
        }
    }

    private static final class ProductAgg {
        private final Long productId;
        private final String name;
        private long quantity;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductAgg(Long productId, String name) {
            this.productId = productId;
            this.name = name;
        }

        private void add(int qty, BigDecimal lineTotal) {
            quantity += qty;
            revenue = revenue.add(lineTotal);
        }

        private AnalyticsDtos.WaiterPerformanceProduct toDto() {
            return new AnalyticsDtos.WaiterPerformanceProduct(
                    productId,
                    name,
                    quantity,
                    revenue
            );
        }
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
            menuSubCategoryRepository.findByIdAndDeletedFalse(item.categoryId())
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
        if (item.type() == MenuAnalyticsEventType.SERVES_FILTER) {
            if (item.servesPeople() == null || item.servesPeople() < 1) {
                throw new BadRequestException("SERVES_FILTER icin servesPeople zorunludur");
            }
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

    private List<MenuWaiter> loadWaiters(ReportScope scope) {
        if (scope.branchId() != null) {
            return menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(scope.branchId());
        }
        return List.of();
    }

    private ReportScope resolveMenuScope(Long menuId, Long ownerId) {
        Menu menu = requireOwnedMenu(menuId, ownerId);
        Long branchId = menu.getBranchId();
        String branchName = null;
        if (branchId != null) {
            branchName = branchRepository.findById(branchId).map(Branch::getName).orElse(null);
        }
        return ReportScope.forMenus(
                branchId,
                branchName,
                menu.getMenuId(),
                menu.getBusinessName(),
                List.of(menu)
        );
    }

    private ReportScope resolveBranchScope(Long branchId, Long menuId, Long ownerId) {
        Branch branch = branchService.requireOwnedForUser(branchId, ownerId);
        List<Menu> menus = menuRepository.findByBranchIdAndDeletedFalse(branch.getId());
        if (menuId != null) {
            Menu menu = menus.stream()
                    .filter(item -> menuId.equals(item.getMenuId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
            return ReportScope.forMenus(
                    branch.getId(),
                    branch.getName(),
                    menu.getMenuId(),
                    menu.getBusinessName(),
                    List.of(menu)
            );
        }
        return ReportScope.forMenus(branch.getId(), branch.getName(), null, null, menus);
    }

    private AnalyticsDtos.MenuAnalyticsReportResponse emptyVisitReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        List<AnalyticsDtos.DailyReportPoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyReportPoint(cursor, 0L, 0L, 0L));
        }
        List<AnalyticsDtos.HourlyReportPoint> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new AnalyticsDtos.HourlyReportPoint(h, 0L));
        }
        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
                from,
                to,
                new AnalyticsDtos.ReportKpis(0L, 0L, 0L, 0L, 0d),
                daily,
                hourly,
                List.of(
                        new AnalyticsDtos.NamedCount("Mobil", 0L),
                        new AnalyticsDtos.NamedCount("Tablet", 0L),
                        new AnalyticsDtos.NamedCount("Masaustu", 0L)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AnalyticsDtos.FunnelCounts(0L, 0L, 0L),
                menuFeedbackService.buildReportFeedback(List.of(), from, to)
        );
    }

    private AnalyticsDtos.MenuRevenueReportResponse emptyRevenueReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        List<AnalyticsDtos.DailyRevenuePoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyRevenuePoint(cursor, BigDecimal.ZERO, 0L));
        }
        List<AnalyticsDtos.HourlyRevenuePoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new AnalyticsDtos.HourlyRevenuePoint(hour, BigDecimal.ZERO, 0L));
        }
        return new AnalyticsDtos.MenuRevenueReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
                from,
                to,
                new AnalyticsDtos.RevenueKpis(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO, "TRY"),
                daily,
                List.of(),
                List.of(),
                new AnalyticsDtos.RevenueSpotlight(null, null, null),
                hourly,
                new AnalyticsDtos.UnsoldCatalog(0L, List.of()),
                new AnalyticsDtos.RevenuePaymentBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "TRY"
                ),
                List.of()
        );
    }

    private AnalyticsDtos.MenuWaiterPerformanceReportResponse emptyWaiterReport(
            ReportScope scope,
            LocalDate from,
            LocalDate to
    ) {
        List<AnalyticsDtos.DailyRevenuePoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyRevenuePoint(cursor, BigDecimal.ZERO, 0L));
        }
        List<AnalyticsDtos.HourlyRevenuePoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new AnalyticsDtos.HourlyRevenuePoint(hour, BigDecimal.ZERO, 0L));
        }
        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                scope.menuId(),
                scope.menuName(),
                scope.branchId(),
                scope.branchName(),
                from,
                to,
                new AnalyticsDtos.WaiterPerformanceKpis(
                        0L, 0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L, "TRY"),
                List.of(),
                daily,
                hourly,
                List.of()
        );
    }

    private Map<Long, String> labeledProductNames(ReportScope scope, List<MenuProduct> products) {
        Map<String, Long> nameCounts = new HashMap<>();
        for (MenuProduct product : products) {
            nameCounts.merge(normalizeLabel(product.getName()), 1L, Long::sum);
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (MenuProduct product : products) {
            String name = product.getName() == null || product.getName().isBlank()
                    ? "Urun #" + product.getProductId()
                    : product.getName();
            if (scope.disambiguateNames() && nameCounts.getOrDefault(normalizeLabel(product.getName()), 0L) > 1) {
                String menuName = scope.menuNamesById().get(product.getMenuId());
                if (menuName != null && !menuName.isBlank()) {
                    name = name + " (" + menuName + ")";
                }
            }
            names.put(product.getProductId(), name);
        }
        return names;
    }

    private Map<Long, String> labeledWaiterNames(ReportScope scope, List<MenuWaiter> waiters) {
        Map<String, Long> nameCounts = new HashMap<>();
        for (MenuWaiter waiter : waiters) {
            nameCounts.merge(normalizeLabel(waiter.getDisplayName()), 1L, Long::sum);
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (MenuWaiter waiter : waiters) {
            String name = waiter.getDisplayName() == null || waiter.getDisplayName().isBlank()
                    ? "Personel #" + waiter.getId()
                    : waiter.getDisplayName();
            if (scope.disambiguateNames() && nameCounts.getOrDefault(normalizeLabel(waiter.getDisplayName()), 0L) > 1) {
                name = name + " #" + waiter.getId();
            }
            names.put(waiter.getId(), name);
        }
        return names;
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ReportScope(
            Long branchId,
            String branchName,
            Long menuId,
            String menuName,
            List<Long> menuIds,
            Map<Long, String> menuNamesById,
            boolean disambiguateNames
    ) {
        private static ReportScope forMenus(
                Long branchId,
                String branchName,
                Long menuId,
                String menuName,
                List<Menu> menus
        ) {
            List<Long> ids = menus.stream().map(Menu::getMenuId).toList();
            Map<Long, String> names = menus.stream()
                    .collect(Collectors.toMap(
                            Menu::getMenuId,
                            menu -> menu.getBusinessName() == null ? "" : menu.getBusinessName(),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
            return new ReportScope(
                    branchId,
                    branchName,
                    menuId,
                    menuName,
                    ids,
                    names,
                    menuId == null && ids.size() > 1
            );
        }
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

    private AnalyticsDtos.RevenueSpotlight buildRevenueSpotlight(List<AnalyticsDtos.RevenueProduct> productRows) {
        if (productRows.isEmpty()) {
            return new AnalyticsDtos.RevenueSpotlight(null, null, null);
        }
        AnalyticsDtos.RevenueProduct byQuantity = productRows.stream()
                .max(Comparator
                        .comparingLong(AnalyticsDtos.RevenueProduct::quantity)
                        .thenComparing(AnalyticsDtos.RevenueProduct::revenue)
                        .thenComparing(AnalyticsDtos.RevenueProduct::productId, Comparator.reverseOrder()))
                .orElse(null);
        AnalyticsDtos.RevenueProduct byRevenue = productRows.stream()
                .max(Comparator
                        .comparing(AnalyticsDtos.RevenueProduct::revenue)
                        .thenComparingLong(AnalyticsDtos.RevenueProduct::quantity)
                        .thenComparing(AnalyticsDtos.RevenueProduct::productId, Comparator.reverseOrder()))
                .orElse(null);
        AnalyticsDtos.RevenueProduct leastSold = productRows.stream()
                .min(Comparator
                        .comparingLong(AnalyticsDtos.RevenueProduct::quantity)
                        .thenComparing(AnalyticsDtos.RevenueProduct::revenue)
                        .thenComparing(AnalyticsDtos.RevenueProduct::productId))
                .orElse(null);
        return new AnalyticsDtos.RevenueSpotlight(
                toSpotlightProduct(byQuantity),
                toSpotlightProduct(byRevenue),
                toSpotlightProduct(leastSold)
        );
    }

    private AnalyticsDtos.RevenueSpotlightProduct toSpotlightProduct(AnalyticsDtos.RevenueProduct row) {
        if (row == null) {
            return null;
        }
        return new AnalyticsDtos.RevenueSpotlightProduct(
                row.productId(),
                row.name(),
                row.quantity(),
                row.revenue()
        );
    }

    private AnalyticsDtos.UnsoldCatalog buildUnsoldCatalog(
            Map<Long, MenuProduct> catalog,
            Set<Long> soldProductIds
    ) {
        List<AnalyticsDtos.UnsoldProduct> unsold = catalog.values().stream()
                .filter(product -> !soldProductIds.contains(product.getProductId()))
                .sorted(Comparator.comparing(MenuProduct::getSortOrder).thenComparing(MenuProduct::getProductId))
                .map(product -> new AnalyticsDtos.UnsoldProduct(product.getProductId(), product.getName()))
                .toList();
        return new AnalyticsDtos.UnsoldCatalog(
                unsold.size(),
                unsold.stream().limit(UNSOLD_LIMIT).toList()
        );
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
            case SERVES_FILTER -> event.getServesPeople() == null
                    ? "Kisi sayisi filtresi"
                    : event.getServesPeople() + " kisilik filtre";
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
