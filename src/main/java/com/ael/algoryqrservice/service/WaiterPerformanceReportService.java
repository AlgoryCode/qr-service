package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.WaiterCommissionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaiterPerformanceReportService {

    private static final int TOP_LIMIT = 10;
    private static final int WAITER_TOP_PRODUCTS = 5;

    private final BillPaymentRepository billPaymentRepository;
    private final TableBillRepository tableBillRepository;
    private final WaiterCommissionRecordRepository commissionRecordRepository;

    public AnalyticsDtos.MenuWaiterPerformanceReportResponse build(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            Collection<Long> menuIds,
            List<MenuWaiter> waiters,
            Map<Long, String> waiterNames,
            LocalDate from,
            LocalDate to
    ) {
        if (menuIds == null || menuIds.isEmpty()) {
            return emptyReport(menuId, menuName, branchId, branchName, from, to);
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);

        ReportContext context = new ReportContext(waiters, waiterNames, from, to);
        applyPayments(context, loadPayments(menuIds, fromDt, toDt));
        applyClosedBills(context, loadClosedBills(menuIds, fromDt, toDt));
        applyCommissions(context, loadCommissions(waiters, fromDt, toDt));
        return assemble(menuId, menuName, branchId, branchName, context);
    }

    private List<BillPayment> loadPayments(Collection<Long> menuIds, LocalDateTime fromDt, LocalDateTime toDt) {
        return billPaymentRepository.findByMenuIdInAndPaidAtBetween(menuIds, fromDt, toDt);
    }

    private List<TableBill> loadClosedBills(Collection<Long> menuIds, LocalDateTime fromDt, LocalDateTime toDt) {
        return tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetween(
                menuIds,
                TableBillStatus.CLOSED,
                fromDt,
                toDt
        );
    }

    private List<WaiterCommissionRecord> loadCommissions(
            List<MenuWaiter> waiters,
            LocalDateTime fromDt,
            LocalDateTime toDt
    ) {
        List<Long> waiterIds = waiters.stream().map(MenuWaiter::getId).toList();
        if (waiterIds.isEmpty()) {
            return List.of();
        }
        return commissionRecordRepository.findByWaiterIdInAndCreatedAtBetween(waiterIds, fromDt, toDt);
    }

    private void applyPayments(ReportContext context, List<BillPayment> payments) {
        for (BillPayment payment : payments) {
            applyPayment(context, payment);
        }
    }

    private void applyPayment(ReportContext context, BillPayment payment) {
        BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        resolveCurrency(context, payment);
        LocalDate day = payment.getPaidAt() != null ? payment.getPaidAt().toLocalDate() : context.from;
        int hour = payment.getPaidAt() != null ? payment.getPaidAt().getHour() : 0;
        Long billId = payment.getBill() != null ? payment.getBill().getId() : null;
        WaiterStats stats = context.statsFor(payment.getWaiterId());

        if (payment.isTip()) {
            context.totalTip = context.totalTip.add(amount);
            stats.addTip(amount);
            context.revenueByDay.merge(day, amount, BigDecimal::add);
            context.revenueByHour.merge(hour, amount, BigDecimal::add);
            return;
        }

        context.totalRevenue = context.totalRevenue.add(amount);
        stats.addRevenue(amount, billId);
        context.revenueByDay.merge(day, amount, BigDecimal::add);
        context.revenueByHour.merge(hour, amount, BigDecimal::add);
        if (billId != null) {
            context.ordersByDay.computeIfAbsent(day, ignored -> new HashSet<>()).add(billId);
            context.ordersByHour.computeIfAbsent(hour, ignored -> new HashSet<>()).add(billId);
        }
        applyPaymentProduct(context, stats, payment, amount);
    }

    private void applyPaymentProduct(
            ReportContext context,
            WaiterStats stats,
            BillPayment payment,
            BigDecimal amount
    ) {
        TableBillItem billItem = payment.getBillItem();
        if (billItem == null) {
            return;
        }
        long qty = payment.getQuantityPaid();
        context.totalItemCount += qty;
        stats.addItem(qty);
        Long productId = billItem.getProductId();
        String productName = billItem.getProductName() != null
                ? billItem.getProductName()
                : "Ürün #" + productId;
        context.aggregateProducts.computeIfAbsent(productId, ignored -> new ProductAgg(productId, productName))
                .add((int) qty, amount);
        stats.products.computeIfAbsent(productId, ignored -> new ProductAgg(productId, productName))
                .add((int) qty, amount);
    }

    private void applyClosedBills(ReportContext context, List<TableBill> closedBills) {
        for (TableBill bill : closedBills) {
            context.billsClosedCount++;
            Long closerId = bill.getClosedByWaiterId();
            if (closerId == null) {
                continue;
            }
            context.statsFor(closerId).billsClosedCount++;
        }
    }

    private void applyCommissions(ReportContext context, List<WaiterCommissionRecord> records) {
        for (WaiterCommissionRecord record : records) {
            BigDecimal amount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
            context.totalCommission = context.totalCommission.add(amount);
            context.statsFor(record.getWaiterId()).addCommission(amount);
        }
    }

    private void resolveCurrency(ReportContext context, BillPayment payment) {
        if (payment.getBill() == null) {
            return;
        }
        String currency = payment.getBill().getCurrency();
        if (currency != null && !currency.isBlank()) {
            context.currency = currency;
        }
    }

    private AnalyticsDtos.MenuWaiterPerformanceReportResponse assemble(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
            ReportContext context
    ) {
        long assignedOrderCount = countAssignedOrders(context);
        long unassignedOrderCount = context.unassigned.billIds.size();
        long totalOrders = assignedOrderCount + unassignedOrderCount;
        List<AnalyticsDtos.WaiterPerformanceRow> rows = buildRows(context, totalOrders);

        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
                menuId,
                menuName,
                branchId,
                branchName,
                context.from,
                context.to,
                new AnalyticsDtos.WaiterPerformanceKpis(
                        context.waiters.stream().filter(MenuWaiter::isActive).count(),
                        assignedOrderCount,
                        unassignedOrderCount,
                        context.totalRevenue,
                        context.totalItemCount,
                        context.totalCommission,
                        context.totalTip,
                        context.billsClosedCount,
                        context.currency
                ),
                rows,
                buildDaily(context),
                buildHourly(context),
                buildTopProducts(context.aggregateProducts)
        );
    }

    private long countAssignedOrders(ReportContext context) {
        return context.statsByWaiterId.values().stream()
                .mapToLong(stats -> stats.billIds.size())
                .sum();
    }

    private List<AnalyticsDtos.WaiterPerformanceRow> buildRows(ReportContext context, long totalOrders) {
        List<AnalyticsDtos.WaiterPerformanceRow> rows = new ArrayList<>();
        Set<Long> knownIds = context.waiters.stream().map(MenuWaiter::getId).collect(Collectors.toSet());

        for (MenuWaiter waiter : context.waiters) {
            WaiterStats stats = context.statsByWaiterId.getOrDefault(waiter.getId(), new WaiterStats());
            rows.add(toRow(
                    waiter.getId(),
                    context.waiterNames.getOrDefault(waiter.getId(), waiter.getDisplayName()),
                    waiter.isActive(),
                    stats,
                    context.totalRevenue,
                    totalOrders,
                    context.totalItemCount
            ));
        }

        for (Map.Entry<Long, WaiterStats> entry : context.statsByWaiterId.entrySet()) {
            if (knownIds.contains(entry.getKey())) {
                continue;
            }
            rows.add(toRow(
                    entry.getKey(),
                    context.waiterNames.getOrDefault(entry.getKey(), "Personel #" + entry.getKey()),
                    true,
                    entry.getValue(),
                    context.totalRevenue,
                    totalOrders,
                    context.totalItemCount
            ));
        }

        if (hasUnassignedActivity(context.unassigned)) {
            rows.add(toRow(
                    null,
                    "Atanmamış",
                    false,
                    context.unassigned,
                    context.totalRevenue,
                    totalOrders,
                    context.totalItemCount
            ));
        }

        rows.sort(Comparator
                .comparing(AnalyticsDtos.WaiterPerformanceRow::revenue)
                .reversed()
                .thenComparing(AnalyticsDtos.WaiterPerformanceRow::displayName));
        return rows;
    }

    private boolean hasUnassignedActivity(WaiterStats stats) {
        return !stats.billIds.isEmpty()
                || stats.revenue.compareTo(BigDecimal.ZERO) > 0
                || stats.tip.compareTo(BigDecimal.ZERO) > 0
                || stats.itemCount > 0;
    }

    private AnalyticsDtos.WaiterPerformanceRow toRow(
            Long waiterId,
            String displayName,
            boolean active,
            WaiterStats stats,
            BigDecimal totalRevenue,
            long totalOrders,
            long totalItemCount
    ) {
        long orderCount = stats.billIds.size();
        BigDecimal avgOrderValue = orderCount == 0
                ? BigDecimal.ZERO
                : stats.revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        return new AnalyticsDtos.WaiterPerformanceRow(
                waiterId,
                displayName,
                orderCount,
                stats.itemCount,
                stats.revenue,
                stats.tip,
                stats.commission,
                stats.billsClosedCount,
                avgOrderValue,
                sharePercent(stats.revenue, totalRevenue),
                sharePercent(BigDecimal.valueOf(orderCount), BigDecimal.valueOf(totalOrders)),
                sharePercent(BigDecimal.valueOf(stats.itemCount), BigDecimal.valueOf(totalItemCount)),
                active,
                buildTopProducts(stats.products).stream().limit(WAITER_TOP_PRODUCTS).toList()
        );
    }

    private double sharePercent(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return 0d;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<AnalyticsDtos.DailyRevenuePoint> buildDaily(ReportContext context) {
        List<AnalyticsDtos.DailyRevenuePoint> daily = new ArrayList<>();
        for (LocalDate cursor = context.from; !cursor.isAfter(context.to); cursor = cursor.plusDays(1)) {
            Set<Long> billIds = context.ordersByDay.getOrDefault(cursor, Set.of());
            daily.add(new AnalyticsDtos.DailyRevenuePoint(
                    cursor,
                    context.revenueByDay.getOrDefault(cursor, BigDecimal.ZERO),
                    billIds.size()
            ));
        }
        return daily;
    }

    private List<AnalyticsDtos.HourlyRevenuePoint> buildHourly(ReportContext context) {
        List<AnalyticsDtos.HourlyRevenuePoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            Set<Long> billIds = context.ordersByHour.getOrDefault(hour, Set.of());
            hourly.add(new AnalyticsDtos.HourlyRevenuePoint(
                    hour,
                    context.revenueByHour.getOrDefault(hour, BigDecimal.ZERO),
                    billIds.size()
            ));
        }
        return hourly;
    }

    private List<AnalyticsDtos.WaiterPerformanceProduct> buildTopProducts(Map<Long, ProductAgg> products) {
        return products.values().stream()
                .map(ProductAgg::toDto)
                .sorted(Comparator.comparing(AnalyticsDtos.WaiterPerformanceProduct::quantity).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    public AnalyticsDtos.MenuWaiterPerformanceReportResponse emptyReport(
            Long menuId,
            String menuName,
            Long branchId,
            String branchName,
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
                menuId,
                menuName,
                branchId,
                branchName,
                from,
                to,
                new AnalyticsDtos.WaiterPerformanceKpis(
                        0L, 0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, "TRY"),
                List.of(),
                daily,
                hourly,
                List.of()
        );
    }

    private static final class ReportContext {
        private final List<MenuWaiter> waiters;
        private final Map<Long, String> waiterNames;
        private final LocalDate from;
        private final LocalDate to;
        private final Map<Long, WaiterStats> statsByWaiterId = new LinkedHashMap<>();
        private final WaiterStats unassigned = new WaiterStats();
        private final Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        private final Map<LocalDate, Set<Long>> ordersByDay = new HashMap<>();
        private final Map<Integer, BigDecimal> revenueByHour = new HashMap<>();
        private final Map<Integer, Set<Long>> ordersByHour = new HashMap<>();
        private final Map<Long, ProductAgg> aggregateProducts = new LinkedHashMap<>();
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal totalTip = BigDecimal.ZERO;
        private BigDecimal totalCommission = BigDecimal.ZERO;
        private long totalItemCount;
        private long billsClosedCount;
        private String currency = "TRY";

        private ReportContext(List<MenuWaiter> waiters, Map<Long, String> waiterNames, LocalDate from, LocalDate to) {
            this.waiters = waiters;
            this.waiterNames = waiterNames;
            this.from = from;
            this.to = to;
            for (MenuWaiter waiter : waiters) {
                statsByWaiterId.put(waiter.getId(), new WaiterStats());
            }
        }

        private WaiterStats statsFor(Long waiterId) {
            if (waiterId == null) {
                return unassigned;
            }
            return statsByWaiterId.computeIfAbsent(waiterId, ignored -> new WaiterStats());
        }
    }

    private static final class WaiterStats {
        private final Set<Long> billIds = new HashSet<>();
        private long itemCount;
        private long billsClosedCount;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal tip = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private final Map<Long, ProductAgg> products = new LinkedHashMap<>();

        private void addRevenue(BigDecimal amount, Long billId) {
            revenue = revenue.add(amount);
            if (billId != null) {
                billIds.add(billId);
            }
        }

        private void addTip(BigDecimal amount) {
            tip = tip.add(amount);
        }

        private void addItem(long qty) {
            itemCount += qty;
        }

        private void addCommission(BigDecimal amount) {
            commission = commission.add(amount);
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
            return new AnalyticsDtos.WaiterPerformanceProduct(productId, name, quantity, revenue);
        }
    }
}
