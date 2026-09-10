package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.BillAdjustmentType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.OrderSource;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.enums.WorkShiftStatus;
import com.ael.algoryqrservice.repository.BillAdjustmentRepository;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UnifiedAnalyticsAggregates {

    private static final int CO_PURCHASE_ANCHORS = 20;
    private static final int CO_PURCHASE_COMPANIONS = 5;
    private static final int DRILL_DOWN_LIMIT = 50;
    private static final int TOP_TABLES = 5;
    private static final long DELAY_THRESHOLD_MINUTES = 20L;
    private static final Set<MenuOrderStatus> CANCEL_STATUSES =
            EnumSet.of(MenuOrderStatus.CANCELLED, MenuOrderStatus.REJECTED);
    private static final Set<MenuOrderStatus> COMPLETED_LIKE = EnumSet.of(
            MenuOrderStatus.CONFIRMED,
            MenuOrderStatus.PREPARING,
            MenuOrderStatus.READY,
            MenuOrderStatus.SERVED
    );

    private final MenuOrderRepository menuOrderRepository;
    private final TableBillRepository tableBillRepository;
    private final TableBillItemRepository tableBillItemRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final MenuWaiterRepository menuWaiterRepository;
    private final MenuAnalyticsSessionRepository sessionRepository;
    private final BillAdjustmentRepository billAdjustmentRepository;
    private final WorkShiftRepository workShiftRepository;

    public long countOpenBills(Collection<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return 0L;
        }
        return tableBillRepository.countByMenuIdInAndStatus(menuIds, TableBillStatus.OPEN);
    }

    public long countActiveTables(Collection<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return 0L;
        }
        return restaurantTableRepository.findByMenuIdInOrderByTableNumberAscNameAsc(menuIds).stream()
                .filter(RestaurantTable::isActive)
                .count();
    }

    public AnalyticsDtos.OrdersAnalytics buildOrders(
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds.isEmpty()) {
            return emptyOrders(from, to);
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<MenuOrder> orders = menuOrderRepository.findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(
                menuIds, fromDt, toDt);

        Map<MenuOrderStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : menuOrderRepository.countByStatusForMenuIds(menuIds, fromDt, toDt)) {
            byStatus.put((MenuOrderStatus) row[0], ((Number) row[1]).longValue());
        }

        List<AnalyticsDtos.StatusCount> statusCounts = new ArrayList<>();
        for (MenuOrderStatus status : MenuOrderStatus.values()) {
            statusCounts.add(new AnalyticsDtos.StatusCount(status.name(), byStatus.getOrDefault(status, 0L)));
        }

        long[] hourly = new long[24];
        Map<LocalDate, Long> dailyMap = new HashMap<>();
        long itemSum = 0L;
        long itemOrderCount = 0L;
        BigDecimal amountSum = BigDecimal.ZERO;
        long completed = 0L;
        long cancelled = 0L;

        for (MenuOrder order : orders) {
            LocalDateTime stamp = orderStamp(order);
            if (stamp != null) {
                hourly[stamp.getHour()]++;
                dailyMap.merge(stamp.toLocalDate(), 1L, Long::sum);
            }
            if (COMPLETED_LIKE.contains(order.getStatus())) {
                completed++;
                amountSum = amountSum.add(nullToZero(order.getTotalAmount()));
                if (order.getItems() != null && !order.getItems().isEmpty()) {
                    itemSum += order.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
                    itemOrderCount++;
                }
            }
            if (CANCEL_STATUSES.contains(order.getStatus())) {
                cancelled++;
            }
        }

        List<AnalyticsDtos.HourlyOrderPoint> hourlyPoints = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourlyPoints.add(new AnalyticsDtos.HourlyOrderPoint(h, hourly[h]));
        }
        List<AnalyticsDtos.DailyOrderPoint> dailyPoints = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            dailyPoints.add(new AnalyticsDtos.DailyOrderPoint(cursor, dailyMap.getOrDefault(cursor, 0L)));
        }

        long decisive = completed + cancelled;
        double cancelRate = decisive == 0 ? 0d : (cancelled * 100.0) / decisive;
        double avgItems = itemOrderCount == 0 ? 0d : (double) itemSum / itemOrderCount;
        BigDecimal aov = completed == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amountSum.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);

        return new AnalyticsDtos.OrdersAnalytics(
                statusCounts,
                hourlyPoints,
                dailyPoints,
                avgItems,
                round2(cancelRate),
                aov,
                orders.size(),
                completed,
                cancelled
        );
    }

    public AnalyticsDtos.TablesAnalytics buildTables(
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds.isEmpty()) {
            return new AnalyticsDtos.TablesAnalytics(List.of(), null, null, 0L, 0L, null, null, null);
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        Map<Long, RestaurantTable> tables = restaurantTableRepository
                .findByMenuIdInOrderByTableNumberAscNameAsc(menuIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getId, t -> t, (a, b) -> a, LinkedHashMap::new));

        List<TableBill> closedBills = tableBillRepository
                .findByMenuIdInAndStatusAndClosedAtBetweenOrderByClosedAtAsc(
                        menuIds, TableBillStatus.CLOSED, fromDt, toDt);

        List<MenuOrder> orders = menuOrderRepository.findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(
                menuIds, fromDt, toDt);

        Map<Long, TableAcc> acc = new HashMap<>();
        for (Long tableId : tables.keySet()) {
            acc.put(tableId, new TableAcc());
        }

        long totalDwellSeconds = 0L;
        long dwellSamples = 0L;
        for (TableBill bill : closedBills) {
            TableAcc row = acc.computeIfAbsent(bill.getTableId(), id -> new TableAcc());
            row.billCount++;
            row.closedBills++;
            row.revenue = row.revenue.add(nullToZero(bill.getTotalAmount()));
            if (bill.getCoverCount() != null && bill.getCoverCount() > 0) {
                row.coverCountTotal += bill.getCoverCount();
            }
            if (bill.getOpenedAt() != null && bill.getClosedAt() != null) {
                long seconds = Duration.between(bill.getOpenedAt(), bill.getClosedAt()).getSeconds();
                if (seconds > 0) {
                    row.dwellSeconds += seconds;
                    row.dwellSamples++;
                    totalDwellSeconds += seconds;
                    dwellSamples++;
                }
            }
        }

        long qrOrders = 0L;
        long staffOrders = 0L;
        for (MenuOrder order : orders) {
            if (!COMPLETED_LIKE.contains(order.getStatus()) && !CANCEL_STATUSES.contains(order.getStatus())) {
                continue;
            }
            TableAcc row = acc.computeIfAbsent(order.getTableId(), id -> new TableAcc());
            row.orderCount++;
            boolean qr = order.getOrderSource() == OrderSource.QR
                    || (order.getOrderSource() == null && order.getCreatedByWaiterId() == null && order.getWaiterId() == null);
            if (qr) {
                row.qrOrderCount++;
                qrOrders++;
            } else {
                row.staffOrderCount++;
                staffOrders++;
            }
        }

        long periodDays = Math.max(1, Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays());
        List<AnalyticsDtos.TableAnalyticsRow> rows = new ArrayList<>();
        for (Map.Entry<Long, TableAcc> entry : acc.entrySet()) {
            Long tableId = entry.getKey();
            TableAcc a = entry.getValue();
            RestaurantTable table = tables.get(tableId);
            String name = table != null ? table.getName() : "Masa #" + tableId;
            BigDecimal avgCheck = a.closedBills == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : a.revenue.divide(BigDecimal.valueOf(a.closedBills), 2, RoundingMode.HALF_UP);
            Double avgDwell = a.dwellSamples == 0
                    ? null
                    : round2(a.dwellSeconds / 60.0 / a.dwellSamples);
            Integer capacity = table == null ? null : table.getCapacity();
            BigDecimal perCover = a.coverCountTotal == 0
                    ? null
                    : a.revenue.divide(BigDecimal.valueOf(a.coverCountTotal), 2, RoundingMode.HALF_UP);
            rows.add(new AnalyticsDtos.TableAnalyticsRow(
                    tableId,
                    name,
                    a.revenue.setScale(2, RoundingMode.HALF_UP),
                    a.orderCount,
                    a.billCount,
                    avgCheck,
                    avgDwell,
                    a.closedBills,
                    a.qrOrderCount,
                    a.staffOrderCount,
                    capacity,
                    a.coverCountTotal == 0 ? null : (int) a.coverCountTotal,
                    perCover
            ));
        }
        rows.sort(Comparator.comparing(AnalyticsDtos.TableAnalyticsRow::revenue).reversed());

        Double avgDwellMinutes = dwellSamples == 0
                ? null
                : round2(totalDwellSeconds / 60.0 / dwellSamples);
        long closedCount = closedBills.size();
        Double turnRate = closedCount == 0
                ? null
                : round2(closedCount / (double) periodDays / Math.max(1, tables.size()));
        long channelOrders = qrOrders + staffOrders;
        Double qrShare = channelOrders == 0 ? null : round2(qrOrders * 100.0 / channelOrders);
        long openBills = countOpenBills(menuIds);
        long activeTables = countActiveTables(menuIds);
        long totalCovers = rows.stream()
                .map(AnalyticsDtos.TableAnalyticsRow::coverCountTotal)
                .filter(c -> c != null && c > 0)
                .mapToLong(Integer::longValue)
                .sum();
        BigDecimal totalRevenue = rows.stream()
                .map(AnalyticsDtos.TableAnalyticsRow::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgPerCover = totalCovers == 0
                ? null
                : totalRevenue.divide(BigDecimal.valueOf(totalCovers), 2, RoundingMode.HALF_UP);

        return new AnalyticsDtos.TablesAnalytics(
                rows,
                avgDwellMinutes,
                turnRate,
                qrOrders,
                staffOrders,
                qrShare,
                occupancyRatio(openBills, activeTables),
                avgPerCover
        );
    }

    public List<AnalyticsDtos.TableAnalyticsRow> topTables(
            AnalyticsDtos.TablesAnalytics tables,
            int limit
    ) {
        return tables.perTable().stream().limit(limit).toList();
    }

    public AnalyticsDtos.ProductsAnalytics buildProducts(
            AnalyticsDtos.MenuRevenueReportResponse revenue,
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        List<AnalyticsDtos.RevenueProduct> products = revenue.products() == null
                ? List.of()
                : revenue.products();
        List<AnalyticsDtos.RevenueProduct> top = products.stream().limit(10).toList();
        List<AnalyticsDtos.RevenueProduct> bottom = products.stream()
                .sorted(Comparator.comparing(AnalyticsDtos.RevenueProduct::quantity)
                        .thenComparing(AnalyticsDtos.RevenueProduct::revenue))
                .limit(10)
                .toList();
        return new AnalyticsDtos.ProductsAnalytics(
                top,
                bottom,
                revenue.categories() == null ? List.of() : revenue.categories(),
                revenue.hourly() == null ? List.of() : revenue.hourly(),
                buildCoPurchase(menuIds, from, to),
                revenue.unsold()
        );
    }

    public List<AnalyticsDtos.ProductCoPurchase> buildCoPurchase(
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<Object[]> rows = tableBillItemRepository.findProductRowsForClosedBills(
                menuIds, TableBillStatus.CLOSED, fromDt, toDt);

        Map<Long, Set<Long>> billProducts = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Object[] row : rows) {
            Long billId = ((Number) row[0]).longValue();
            Long productId = ((Number) row[1]).longValue();
            String name = row[2] == null ? "Urun #" + productId : row[2].toString();
            billProducts.computeIfAbsent(billId, id -> new HashSet<>()).add(productId);
            names.putIfAbsent(productId, name);
        }

        Map<Long, Long> anchorBills = new HashMap<>();
        Map<String, Long> pairCounts = new HashMap<>();
        for (Set<Long> products : billProducts.values()) {
            if (products.size() < 2) {
                continue;
            }
            List<Long> ids = new ArrayList<>(products);
            for (Long productId : ids) {
                anchorBills.merge(productId, 1L, Long::sum);
            }
            for (int i = 0; i < ids.size(); i++) {
                for (int j = 0; j < ids.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    pairCounts.merge(ids.get(i) + ":" + ids.get(j), 1L, Long::sum);
                }
            }
        }

        return anchorBills.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(CO_PURCHASE_ANCHORS)
                .map(entry -> {
                    Long anchorId = entry.getKey();
                    long billCount = entry.getValue();
                    List<AnalyticsDtos.ProductCoPurchaseCompanion> companions = pairCounts.entrySet().stream()
                            .filter(e -> e.getKey().startsWith(anchorId + ":"))
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(CO_PURCHASE_COMPANIONS)
                            .map(e -> {
                                Long companionId = Long.parseLong(e.getKey().substring(e.getKey().indexOf(':') + 1));
                                double share = billCount == 0 ? 0d : e.getValue() * 100.0 / billCount;
                                return new AnalyticsDtos.ProductCoPurchaseCompanion(
                                        companionId,
                                        names.getOrDefault(companionId, "Urun #" + companionId),
                                        e.getValue(),
                                        round2(share)
                                );
                            })
                            .toList();
                    return new AnalyticsDtos.ProductCoPurchase(
                            anchorId,
                            names.getOrDefault(anchorId, "Urun #" + anchorId),
                            billCount,
                            companions
                    );
                })
                .toList();
    }

    public AnalyticsDtos.CancellationsAnalytics buildCancellations(
            Collection<Long> menuIds,
            Long branchId,
            LocalDate from,
            LocalDate to,
            AnalyticsDtos.OrdersAnalytics orders
    ) {
        if (menuIds.isEmpty()) {
            return new AnalyticsDtos.CancellationsAnalytics(List.of(), List.of(), 0L, 0L);
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        long cancelled = orders.countsByStatus().stream()
                .filter(s -> MenuOrderStatus.CANCELLED.name().equals(s.status()))
                .mapToLong(AnalyticsDtos.StatusCount::count)
                .findFirst()
                .orElse(0L);
        long rejected = orders.countsByStatus().stream()
                .filter(s -> MenuOrderStatus.REJECTED.name().equals(s.status()))
                .mapToLong(AnalyticsDtos.StatusCount::count)
                .findFirst()
                .orElse(0L);

        List<AnalyticsDtos.StatusCount> byStatus = List.of(
                new AnalyticsDtos.StatusCount(MenuOrderStatus.CANCELLED.name(), cancelled),
                new AnalyticsDtos.StatusCount(MenuOrderStatus.REJECTED.name(), rejected)
        );

        Map<Long, String> waiterNames = menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(branchId).stream()
                .collect(Collectors.toMap(MenuWaiter::getId, MenuWaiter::getDisplayName, (a, b) -> a));

        List<AnalyticsDtos.CancellationByWaiter> byWaiter = menuOrderRepository
                .countCancellationsByWaiter(menuIds, CANCEL_STATUSES, fromDt, toDt)
                .stream()
                .map(row -> {
                    Long waiterId = row[0] == null ? null : ((Number) row[0]).longValue();
                    long count = ((Number) row[1]).longValue();
                    String name = waiterId == null
                            ? "Atanmamis"
                            : waiterNames.getOrDefault(waiterId, "Personel #" + waiterId);
                    return new AnalyticsDtos.CancellationByWaiter(waiterId, name, count);
                })
                .sorted(Comparator.comparingLong(AnalyticsDtos.CancellationByWaiter::cancelCount).reversed())
                .toList();

        return new AnalyticsDtos.CancellationsAnalytics(byStatus, byWaiter, cancelled, rejected);
    }

    public AnalyticsDtos.CustomersAnalytics buildCustomers(
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds.isEmpty()) {
            return new AnalyticsDtos.CustomersAnalytics(0L, 0L, 0L, 0L, 0L, 0L, null);
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        long identifiedOrders = menuOrderRepository.countIdentifiedCustomerOrders(menuIds, fromDt, toDt);
        long distinctCustomers = menuOrderRepository.countDistinctCustomers(menuIds, fromDt, toDt);
        long sessions = sessionRepository.countByMenuIdInAndPeriod(menuIds, fromDt, toDt);
        long distinctIps = sessionRepository.countDistinctIpHashByMenuIdInAndPeriod(menuIds, fromDt, toDt);
        long returning = sessionRepository.countRepeatSessionGroupsByIpHash(menuIds, fromDt, toDt).size();
        long newVisitors = Math.max(0L, distinctIps - returning);
        long totalOrders = menuOrderRepository.countByStatusForMenuIds(menuIds, fromDt, toDt).stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();
        Double identifiedShare = totalOrders == 0
                ? null
                : round2(identifiedOrders * 100.0 / totalOrders);
        return new AnalyticsDtos.CustomersAnalytics(
                identifiedOrders,
                distinctCustomers,
                sessions,
                returning,
                newVisitors,
                returning,
                identifiedShare
        );
    }

    public AnalyticsDtos.FinanceAnalytics buildFinance(
            AnalyticsDtos.MenuRevenueReportResponse revenue,
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        AnalyticsDtos.RevenuePaymentBreakdown pb = revenue.paymentBreakdown();
        BigDecimal gross = pb == null || pb.grossRevenue() == null ? BigDecimal.ZERO : pb.grossRevenue();
        BigDecimal tips = pb == null || pb.tipRevenue() == null ? BigDecimal.ZERO : pb.tipRevenue();
        BigDecimal cash = pb == null || pb.cashRevenue() == null ? BigDecimal.ZERO : pb.cashRevenue();
        BigDecimal card = pb == null || pb.cardRevenue() == null ? BigDecimal.ZERO : pb.cardRevenue();
        BigDecimal fixed = pb == null || pb.fixedExpenseTotal() == null ? BigDecimal.ZERO : pb.fixedExpenseTotal();
        BigDecimal net = pb == null || pb.netRevenue() == null ? BigDecimal.ZERO : pb.netRevenue();
        String currency = pb != null && pb.currency() != null ? pb.currency() : "TRY";

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        BigDecimal orderTotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal voids = BigDecimal.ZERO;
        if (!menuIds.isEmpty()) {
            orderTotal = menuOrderRepository
                    .findByMenuIdInAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
                            menuIds, MenuOrderStatus.CONFIRMED, fromDt, toDt)
                    .stream()
                    .map(o -> nullToZero(o.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            discount = nullToZero(billAdjustmentRepository.sumByType(
                    menuIds, BillAdjustmentType.DISCOUNT, fromDt, toDt)).setScale(2, RoundingMode.HALF_UP);
            refund = nullToZero(billAdjustmentRepository.sumByType(
                    menuIds, BillAdjustmentType.REFUND, fromDt, toDt)).setScale(2, RoundingMode.HALF_UP);
            voids = nullToZero(billAdjustmentRepository.sumByType(
                    menuIds, BillAdjustmentType.VOID, fromDt, toDt)).setScale(2, RoundingMode.HALF_UP);
            net = net.subtract(discount).subtract(refund).setScale(2, RoundingMode.HALF_UP);
        }

        return new AnalyticsDtos.FinanceAnalytics(
                gross,
                tips,
                cash,
                card,
                fixed,
                net,
                orderTotal,
                gross,
                discount,
                refund,
                voids,
                revenue.channels() == null ? List.of() : revenue.channels(),
                pb,
                currency
        );
    }

    public AnalyticsDtos.KitchenAnalytics buildKitchen(Collection<Long> menuIds, LocalDate from, LocalDate to) {
        if (menuIds.isEmpty()) {
            return emptyKitchen();
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<MenuOrder> orders = menuOrderRepository.findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(
                menuIds, fromDt, toDt);

        long prepSamples = 0;
        long readySamples = 0;
        long serveSamples = 0;
        long totalSamples = 0;
        double prepSum = 0;
        double readySum = 0;
        double serveSum = 0;
        double totalSum = 0;
        long delayed = 0;
        long preparing = 0;
        long ready = 0;
        long served = 0;
        long[] hourlyCount = new long[24];
        double[] hourlyPrep = new double[24];
        long[] hourlyPrepSamples = new long[24];

        for (MenuOrder order : orders) {
            if (order.getStatus() == MenuOrderStatus.PREPARING) {
                preparing++;
            } else if (order.getStatus() == MenuOrderStatus.READY) {
                ready++;
            } else if (order.getStatus() == MenuOrderStatus.SERVED) {
                served++;
            }
            if (order.getConfirmedAt() != null && order.getPreparedAt() != null) {
                double mins = Duration.between(order.getConfirmedAt(), order.getPreparedAt()).toMinutes();
                if (mins >= 0) {
                    prepSum += mins;
                    prepSamples++;
                    int hour = order.getConfirmedAt().getHour();
                    hourlyCount[hour]++;
                    hourlyPrep[hour] += mins;
                    hourlyPrepSamples[hour]++;
                }
            }
            if (order.getPreparedAt() != null && order.getReadyAt() != null) {
                double mins = Duration.between(order.getPreparedAt(), order.getReadyAt()).toMinutes();
                if (mins >= 0) {
                    readySum += mins;
                    readySamples++;
                }
            }
            if (order.getReadyAt() != null && order.getServedAt() != null) {
                double mins = Duration.between(order.getReadyAt(), order.getServedAt()).toMinutes();
                if (mins >= 0) {
                    serveSum += mins;
                    serveSamples++;
                }
            }
            if (order.getConfirmedAt() != null && order.getServedAt() != null) {
                double mins = Duration.between(order.getConfirmedAt(), order.getServedAt()).toMinutes();
                if (mins >= 0) {
                    totalSum += mins;
                    totalSamples++;
                    if (mins > DELAY_THRESHOLD_MINUTES) {
                        delayed++;
                    }
                }
            }
        }

        List<AnalyticsDtos.KitchenHourlyLoad> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Double avg = hourlyPrepSamples[h] == 0 ? null : round2(hourlyPrep[h] / hourlyPrepSamples[h]);
            hourly.add(new AnalyticsDtos.KitchenHourlyLoad(h, hourlyCount[h], avg));
        }

        double delayRate = totalSamples == 0 ? 0d : round2(delayed * 100.0 / totalSamples);
        return new AnalyticsDtos.KitchenAnalytics(
                new AnalyticsDtos.KitchenStageKpis(
                        prepSamples == 0 ? null : round2(prepSum / prepSamples),
                        readySamples == 0 ? null : round2(readySum / readySamples),
                        serveSamples == 0 ? null : round2(serveSum / serveSamples),
                        totalSamples == 0 ? null : round2(totalSum / totalSamples),
                        preparing,
                        ready,
                        served,
                        delayed,
                        delayRate
                ),
                hourly,
                DELAY_THRESHOLD_MINUTES
        );
    }

    public AnalyticsDtos.ShiftsAnalytics buildShifts(Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<AnalyticsDtos.ShiftSummaryRow> rows = workShiftRepository
                .findByBranchIdAndOpenedAtBetweenOrderByOpenedAtDesc(branchId, fromDt, toDt)
                .stream()
                .map(shift -> {
                    LocalDateTime end = shift.getClosedAt() == null ? toDt : shift.getClosedAt();
                    List<Long> menuIds = shift.getMenuId() == null
                            ? List.of()
                            : List.of(shift.getMenuId());
                    BigDecimal revenue = BigDecimal.ZERO;
                    long orderCount = 0L;
                    if (!menuIds.isEmpty()) {
                        revenue = billPaymentRepository.findByMenuIdInAndPaidAtBetween(menuIds, shift.getOpenedAt(), end)
                                .stream()
                                .map(BillPayment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP);
                        orderCount = menuOrderRepository
                                .findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(menuIds, shift.getOpenedAt(), end)
                                .size();
                    }
                    return new AnalyticsDtos.ShiftSummaryRow(
                            shift.getId(),
                            shift.getOpenedAt(),
                            shift.getClosedAt(),
                            shift.getStatus() == null ? WorkShiftStatus.OPEN.name() : shift.getStatus().name(),
                            shift.getOpeningFloat(),
                            shift.getClosingCash(),
                            revenue,
                            orderCount,
                            shift.getWaiterIds() == null ? 0L : shift.getWaiterIds().size()
                    );
                })
                .toList();
        BigDecimal totalRevenue = rows.stream()
                .map(AnalyticsDtos.ShiftSummaryRow::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrders = rows.stream().mapToLong(AnalyticsDtos.ShiftSummaryRow::orderCount).sum();
        return new AnalyticsDtos.ShiftsAnalytics(rows, totalRevenue, totalOrders);
    }

    private AnalyticsDtos.KitchenAnalytics emptyKitchen() {
        List<AnalyticsDtos.KitchenHourlyLoad> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new AnalyticsDtos.KitchenHourlyLoad(h, 0L, null));
        }
        return new AnalyticsDtos.KitchenAnalytics(
                new AnalyticsDtos.KitchenStageKpis(null, null, null, null, 0L, 0L, 0L, 0L, 0d),
                hourly,
                DELAY_THRESHOLD_MINUTES
        );
    }

    public List<AnalyticsDtos.DrillDownLink> buildDrillDownHints(
            Collection<Long> menuIds,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        return billPaymentRepository.findByMenuIdInAndPaidAtBetween(menuIds, fromDt, toDt).stream()
                .limit(DRILL_DOWN_LIMIT)
                .map(this::toDrillDown)
                .toList();
    }

    public Double occupancyRatio(long openBills, long activeTables) {
        if (activeTables <= 0) {
            return null;
        }
        return round2(Math.min(100d, openBills * 100.0 / activeTables));
    }

    public int topTablesLimit() {
        return TOP_TABLES;
    }

    private AnalyticsDtos.DrillDownLink toDrillDown(BillPayment payment) {
        TableBill bill = payment.getBill();
        Long productId = payment.getBillItem() == null ? null : payment.getBillItem().getProductId();
        Long orderId = payment.getBillItem() == null ? null : payment.getBillItem().getSourceOrderId();
        return new AnalyticsDtos.DrillDownLink(
                bill == null ? null : bill.getId(),
                orderId,
                bill == null ? null : bill.getTableId(),
                payment.getWaiterId(),
                productId,
                payment.getPaidAt(),
                payment.getAmount()
        );
    }

    private AnalyticsDtos.OrdersAnalytics emptyOrders(LocalDate from, LocalDate to) {
        List<AnalyticsDtos.StatusCount> statusCounts = new ArrayList<>();
        for (MenuOrderStatus status : MenuOrderStatus.values()) {
            statusCounts.add(new AnalyticsDtos.StatusCount(status.name(), 0L));
        }
        List<AnalyticsDtos.HourlyOrderPoint> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourly.add(new AnalyticsDtos.HourlyOrderPoint(h, 0L));
        }
        List<AnalyticsDtos.DailyOrderPoint> daily = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            daily.add(new AnalyticsDtos.DailyOrderPoint(cursor, 0L));
        }
        return new AnalyticsDtos.OrdersAnalytics(
                statusCounts,
                hourly,
                daily,
                0d,
                0d,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0L,
                0L,
                0L
        );
    }

    private static LocalDateTime orderStamp(MenuOrder order) {
        if (order.getConfirmedAt() != null) {
            return order.getConfirmedAt();
        }
        if (order.getSubmittedAt() != null) {
            return order.getSubmittedAt();
        }
        return order.getCreatedAt();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class TableAcc {
        private BigDecimal revenue = BigDecimal.ZERO;
        private long orderCount;
        private long billCount;
        private long closedBills;
        private long dwellSeconds;
        private long dwellSamples;
        private long qrOrderCount;
        private long staffOrderCount;
        private long coverCountTotal;
    }
}
