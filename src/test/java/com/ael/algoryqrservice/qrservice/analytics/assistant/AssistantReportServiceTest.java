package com.ael.algoryqrservice.qrservice.analytics.assistant;

import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.DashboardAssistantReport;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantReportDtos.MenuAssistantReport;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageOutcome;
import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageTopic;
import com.ael.algoryqrservice.qrservice.model.AssistantUsageEvent;
import com.ael.algoryqrservice.qrservice.model.enums.MenuAnalyticsEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantReportServiceTest {

    private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 11, 19, 30);

    @Test
    void menuReportSumsOnlyAssistantUnitsPerOrderAndProduct() {
        List<Object[]> lines = List.of(
                new Object[]{1L, 10L, "Latte", new BigDecimal("80.00"), 2, DAY1},
                new Object[]{1L, 11L, "Cheesecake", new BigDecimal("150.00"), 1, DAY1},
                new Object[]{2L, 10L, "Latte", new BigDecimal("80.00"), 1, DAY2}
        );

        MenuAssistantReport report = AssistantReportService.buildMenuReport(
                7,
                Map.of(MenuAnalyticsEventType.CHEF_CHAT_OPEN, 9L, MenuAnalyticsEventType.CHEF_PRODUCT_ADD, 4L),
                lines,
                new Object[]{5L, new BigDecimal("2000.00")}
        );

        assertThat(report.ordersWithAssistant()).isEqualTo(2);
        assertThat(report.assistantUnits()).isEqualTo(4);
        assertThat(report.assistantRevenue()).isEqualByComparingTo("390.00");
        assertThat(report.orderCount()).isEqualTo(5);
        assertThat(report.orderRevenue()).isEqualByComparingTo("2000.00");
        assertThat(report.chatSessions()).isEqualTo(7);
        assertThat(report.chatOpens()).isEqualTo(9);
        assertThat(report.productAdds()).isEqualTo(4);
        assertThat(report.crossSellAdds()).isZero();
        assertThat(report.daily()).hasSize(2);
        assertThat(report.daily().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(report.daily().get(0).revenue()).isEqualByComparingTo("310.00");
        assertThat(report.topProducts().get(0).productName()).isEqualTo("Latte");
        assertThat(report.topProducts().get(0).units()).isEqualTo(3);
        assertThat(report.topProducts().get(0).revenue()).isEqualByComparingTo("240.00");
    }

    @Test
    void menuReportIsEmptyWithoutAssistantLines() {
        MenuAssistantReport report = AssistantReportService.buildMenuReport(
                0, Map.of(), List.of(), new Object[]{0L, BigDecimal.ZERO});

        assertThat(report.assistantRevenue()).isEqualByComparingTo("0");
        assertThat(report.ordersWithAssistant()).isZero();
        assertThat(report.daily()).isEmpty();
        assertThat(report.topProducts()).isEmpty();
    }

    @Test
    void dashboardReportCountsTimeSavedOnlyForAnsweredQuestions() {
        List<AssistantUsageEvent> events = List.of(
                event(AssistantUsageTopic.REVENUE, AssistantUsageOutcome.ANSWERED, DAY1, 2000),
                event(AssistantUsageTopic.REVENUE, AssistantUsageOutcome.ANSWERED, DAY1, 4000),
                event(AssistantUsageTopic.STAFF, AssistantUsageOutcome.FAILED, DAY2, 30_000),
                event(AssistantUsageTopic.GENERAL, AssistantUsageOutcome.ANSWERED, DAY2, 3000)
        );

        DashboardAssistantReport report = AssistantReportService.buildDashboardReport(events);

        assertThat(report.questions()).isEqualTo(4);
        assertThat(report.answered()).isEqualTo(3);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.activeDays()).isEqualTo(2);
        assertThat(report.estimatedMinutesSaved()).isEqualTo(4 + 4 + 2);
        assertThat(report.averageResponseMs()).isEqualTo(3000);
        assertThat(report.topics().get(0).topic()).isEqualTo(AssistantUsageTopic.REVENUE);
        assertThat(report.topics().get(0).questions()).isEqualTo(2);
        assertThat(report.topics().get(0).minutesSaved()).isEqualTo(8);
        assertThat(report.daily()).extracting(d -> d.minutesSaved()).containsExactly(8L, 2L);
    }

    @Test
    void dashboardReportWithoutUsageHasNoAverage() {
        DashboardAssistantReport report = AssistantReportService.buildDashboardReport(List.of());

        assertThat(report.questions()).isZero();
        assertThat(report.averageResponseMs()).isNull();
        assertThat(report.topics()).isEmpty();
    }

    private static AssistantUsageEvent event(
            AssistantUsageTopic topic, AssistantUsageOutcome outcome, LocalDateTime at, Integer responseMs) {
        return AssistantUsageEvent.builder()
                .merchantId(1L)
                .branchId(2L)
                .topic(topic)
                .outcome(outcome)
                .createdAt(at)
                .responseMs(responseMs)
                .build();
    }
}
