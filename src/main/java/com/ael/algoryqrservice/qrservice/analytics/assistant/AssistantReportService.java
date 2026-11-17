package com.ael.algoryqrservice.qrservice.analytics.assistant;

import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.AssistantReportResponse;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.DailyRevenue;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.DailyUsage;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.DashboardAssistantReport;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.MenuAssistantReport;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.ProductRevenue;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.RecordUsageRequest;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.TopicUsage;
import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.model.AssistantUsageEvent;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.qrservice.repository.AssistantUsageEventRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Records dashboard assistant questions and reports what both AI assistants did for a branch. */
@Service
@RequiredArgsConstructor
public class AssistantReportService {

    /** Orders that reached the kitchen or the bill; drafts, rejections and cancellations do not count. */
    static final List<MenuOrderStatus> PLACED_STATUSES = List.of(
            MenuOrderStatus.SUBMITTED,
            MenuOrderStatus.CONFIRMED,
            MenuOrderStatus.PREPARING,
            MenuOrderStatus.READY,
            MenuOrderStatus.SERVED,
            MenuOrderStatus.PAID
    );

    private static final List<MenuAnalyticsEventType> CHEF_EVENTS = List.of(
            MenuAnalyticsEventType.CHEF_CHAT_OPEN,
            MenuAnalyticsEventType.CHEF_RECOMMENDATION_REQUEST,
            MenuAnalyticsEventType.CHEF_RECOMMENDATION_SHOWN,
            MenuAnalyticsEventType.CHEF_PRODUCT_CARD_CLICK,
            MenuAnalyticsEventType.CHEF_PRODUCT_ADD,
            MenuAnalyticsEventType.CHEF_CROSS_SELL_SHOWN,
            MenuAnalyticsEventType.CHEF_CROSS_SELL_ADD
    );

    private static final int TOP_PRODUCTS = 5;

    private final AssistantUsageEventRepository usageRepository;
    private final MenuAnalyticsEventRepository eventRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final MenuRepository menuRepository;
    private final BranchService branchService;

    @Transactional
    public void recordUsage(Long merchantId, RecordUsageRequest request) {
        if (request.branchId() != null) {
            branchService.requireOwnedForMerchant(request.branchId(), merchantId);
        }
        usageRepository.save(AssistantUsageEvent.builder()
                .merchantId(merchantId)
                .branchId(request.branchId())
                .surface(AssistantUsageSurface.DASHBOARD)
                .topic(request.topic())
                .outcome(request.outcome())
                .responseMs(request.responseMs())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public AssistantReportDtos.TodayUsageResponse todayUsage(Long merchantId) {
        LocalDate today = LocalDate.now();
        long answered = usageRepository.countByMerchantIdAndOutcomeAndCreatedAtGreaterThanEqual(
                merchantId, AssistantUsageOutcome.ANSWERED, today.atStartOfDay());
        return new AssistantReportDtos.TodayUsageResponse(today, answered);
    }

    @Transactional(readOnly = true)
    public AssistantReportResponse branchReport(Long branchId, Long merchantId, LocalDate from, LocalDate to) {
        Branch branch = branchService.requireOwnedForMerchant(branchId, merchantId);
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<Long> menuIds = menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR)
                .stream()
                .map(Menu::getMenuId)
                .toList();

        MenuAssistantReport menu = menuIds.isEmpty()
                ? buildMenuReport(0, Map.of(), List.of(), new Object[]{0L, BigDecimal.ZERO})
                : buildMenuReport(
                        eventRepository.countDistinctSessions(
                                menuIds, MenuAnalyticsEventType.CHEF_CHAT_OPEN, fromDt, toDt),
                        eventCounts(eventRepository.countByEventTypes(menuIds, CHEF_EVENTS, fromDt, toDt)),
                        menuOrderRepository.findAssistantLines(menuIds, PLACED_STATUSES, fromDt, toDt),
                        firstRow(menuOrderRepository.placedOrderTotals(menuIds, PLACED_STATUSES, fromDt, toDt))
                );
        DashboardAssistantReport dashboard = buildDashboardReport(
                usageRepository.findByMerchantIdAndBranchIdAndCreatedAtBetween(merchantId, branchId, fromDt, toDt));

        return new AssistantReportResponse(
                branch.getId(), branch.getName(), from, to, "TRY", menu, dashboard);
    }

    static MenuAssistantReport buildMenuReport(
            long chatSessions,
            Map<MenuAnalyticsEventType, Long> counts,
            List<Object[]> assistantLines,
            Object[] orderTotals
    ) {
        Set<Long> orders = new HashSet<>();
        long units = 0L;
        BigDecimal revenue = BigDecimal.ZERO;
        Map<LocalDate, long[]> unitsByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        Map<Long, ProductAgg> byProduct = new LinkedHashMap<>();

        for (Object[] row : assistantLines) {
            Long orderId = ((Number) row[0]).longValue();
            Long productId = ((Number) row[1]).longValue();
            String productName = (String) row[2];
            BigDecimal unitPrice = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            int lineUnits = ((Number) row[4]).intValue();
            LocalDateTime submittedAt = (LocalDateTime) row[5];
            BigDecimal lineRevenue = unitPrice.multiply(BigDecimal.valueOf(lineUnits));

            orders.add(orderId);
            units += lineUnits;
            revenue = revenue.add(lineRevenue);
            if (submittedAt != null) {
                LocalDate day = submittedAt.toLocalDate();
                unitsByDay.computeIfAbsent(day, d -> new long[1])[0] += lineUnits;
                revenueByDay.merge(day, lineRevenue, BigDecimal::add);
            }
            byProduct.computeIfAbsent(productId, id -> new ProductAgg(id, productName)).add(lineUnits, lineRevenue);
        }

        List<DailyRevenue> daily = new ArrayList<>();
        revenueByDay.forEach((day, amount) -> daily.add(new DailyRevenue(day, unitsByDay.get(day)[0], amount)));
        List<ProductRevenue> topProducts = byProduct.values().stream()
                .sorted(Comparator.comparing((ProductAgg agg) -> agg.revenue).reversed()
                        .thenComparing(agg -> agg.productId))
                .limit(TOP_PRODUCTS)
                .map(agg -> new ProductRevenue(agg.productId, agg.productName, agg.units, agg.revenue))
                .toList();

        long orderCount = orderTotals[0] != null ? ((Number) orderTotals[0]).longValue() : 0L;
        BigDecimal orderRevenue = orderTotals[1] instanceof BigDecimal amount ? amount : BigDecimal.ZERO;

        return new MenuAssistantReport(
                chatSessions,
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_CHAT_OPEN, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_RECOMMENDATION_REQUEST, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_RECOMMENDATION_SHOWN, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_PRODUCT_CARD_CLICK, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_PRODUCT_ADD, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_CROSS_SELL_SHOWN, 0L),
                counts.getOrDefault(MenuAnalyticsEventType.CHEF_CROSS_SELL_ADD, 0L),
                orders.size(),
                units,
                revenue,
                orderCount,
                orderRevenue,
                daily,
                topProducts
        );
    }

    static DashboardAssistantReport buildDashboardReport(List<AssistantUsageEvent> events) {
        long answered = 0L;
        long failed = 0L;
        long minutesSaved = 0L;
        long responseTotal = 0L;
        long responseCount = 0L;
        Set<LocalDate> activeDays = new HashSet<>();
        Map<AssistantUsageTopic, long[]> byTopic = new EnumMap<>(AssistantUsageTopic.class);
        Map<LocalDate, long[]> byDay = new TreeMap<>();

        for (AssistantUsageEvent event : events) {
            LocalDate day = event.getCreatedAt().toLocalDate();
            activeDays.add(day);
            long[] dayAgg = byDay.computeIfAbsent(day, d -> new long[2]);
            dayAgg[0]++;
            long[] topicAgg = byTopic.computeIfAbsent(event.getTopic(), t -> new long[2]);
            topicAgg[0]++;
            if (event.getOutcome() == AssistantUsageOutcome.ANSWERED) {
                answered++;
                int saved = event.getTopic().minutesSaved();
                minutesSaved += saved;
                dayAgg[1] += saved;
                topicAgg[1] += saved;
                // Only answered questions: a failure's timing says nothing about how fast answers come.
                if (event.getResponseMs() != null) {
                    responseTotal += event.getResponseMs();
                    responseCount++;
                }
            } else {
                failed++;
            }
        }

        List<TopicUsage> topics = byTopic.entrySet().stream()
                .sorted(Map.Entry.<AssistantUsageTopic, long[]>comparingByValue(
                        Comparator.comparingLong((long[] agg) -> agg[0])).reversed())
                .map(entry -> new TopicUsage(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
        List<DailyUsage> daily = new ArrayList<>();
        byDay.forEach((day, agg) -> daily.add(new DailyUsage(day, agg[0], agg[1])));

        return new DashboardAssistantReport(
                events.size(),
                answered,
                failed,
                activeDays.size(),
                minutesSaved,
                responseCount > 0 ? (int) (responseTotal / responseCount) : null,
                topics,
                daily
        );
    }

    private static Map<MenuAnalyticsEventType, Long> eventCounts(List<Object[]> rows) {
        Map<MenuAnalyticsEventType, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((MenuAnalyticsEventType) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static Object[] firstRow(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO} : rows.get(0);
    }

    private static final class ProductAgg {
        private final Long productId;
        private final String productName;
        private long units;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductAgg(Long productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }

        private void add(long lineUnits, BigDecimal lineRevenue) {
            units += lineUnits;
            revenue = revenue.add(lineRevenue);
        }
    }
}
