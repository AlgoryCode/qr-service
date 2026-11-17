package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.RevenueQueryRepository;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Recurring revenue, collections and churn for the panel's revenue page. */
@Service
@RequiredArgsConstructor
public class RevenueService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PurchaseRepository purchaseRepository;
    private final RevenueQueryRepository revenueQueryRepository;

    public record Month(String month, BigDecimal collected, BigDecimal refunded, BigDecimal net, long payments,
                        int newCustomers, int churned, int activeAtStart, BigDecimal mrr) {
    }

    public record PackageShare(String packageName, int customers, BigDecimal mrr) {
    }

    public record Summary(BigDecimal mrr, BigDecimal arr, int activeCustomers, BigDecimal arpa,
                          BigDecimal churnRateLastMonth, BigDecimal dueNext30Days, int dueNext30Count,
                          BigDecimal overdueAmount, int overdueCount, String currency,
                          List<Month> months, List<PackageShare> byPackage) {
    }

    @Transactional(readOnly = true)
    public Summary summary(int monthsBack) {
        int months = Math.min(Math.max(monthsBack, 1), 36);
        LocalDateTime now = AppTime.nowLocal();
        YearMonth thisMonth = YearMonth.from(now);
        YearMonth first = thisMonth.minusMonths(months - 1L);

        List<Purchase> history = purchaseRepository.findPaidPackageHistory();
        List<Purchase> current = AdminBillingService.currentPerMerchant(history.stream()
                .filter(p -> p.getStatus() == PurchaseStatus.ACTIVE).toList());
        List<Purchase> paying = current.stream()
                .filter(p -> AdminBillingService.standing(p, now, 30) != Standing.LAPSED)
                .toList();

        BigDecimal mrr = sum(paying.stream().map(RevenueService::monthly));
        BigDecimal dueNext30 = BigDecimal.ZERO;
        int dueCount = 0;
        BigDecimal overdue = BigDecimal.ZERO;
        int overdueCount = 0;
        for (Purchase p : paying) {
            Standing standing = AdminBillingService.standing(p, now, 30);
            BigDecimal renewal = DunningService.renewalAmount(p);
            if (standing == Standing.DUE_SOON && renewal != null) {
                dueNext30 = dueNext30.add(renewal);
                dueCount++;
            } else if (standing == Standing.OVERDUE && renewal != null) {
                overdue = overdue.add(renewal);
                overdueCount++;
            }
        }

        Map<String, RevenueQueryRepository.MonthTotal> collected = revenueQueryRepository
                .collectedByMonth(first.atDay(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(RevenueQueryRepository.MonthTotal::getMonth, t -> t, (a, b) -> a));

        Map<Long, LocalDateTime> firstStart = history.stream().collect(Collectors.toMap(
                Purchase::getMerchantId, Purchase::getStartsAt, (a, b) -> a.isBefore(b) ? a : b));
        Set<Long> activeNow = paying.stream().map(Purchase::getMerchantId).collect(Collectors.toSet());
        Map<Long, LocalDateTime> lastEnd = history.stream()
                .filter(p -> p.getExpiresAt() != null && !activeNow.contains(p.getMerchantId()))
                .collect(Collectors.toMap(Purchase::getMerchantId, Purchase::getExpiresAt,
                        (a, b) -> a.isAfter(b) ? a : b));

        List<Month> series = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(thisMonth); m = m.plusMonths(1)) {
            LocalDateTime start = m.atDay(1).atStartOfDay();
            LocalDateTime end = m.plusMonths(1).atDay(1).atStartOfDay();
            LocalDateTime at = end.isAfter(now) ? now : end.minusNanos(1);
            YearMonth month = m;
            RevenueQueryRepository.MonthTotal total = collected.get(MONTH.format(m));
            BigDecimal gross = total == null ? BigDecimal.ZERO : total.getGross();
            BigDecimal refunded = total == null ? BigDecimal.ZERO : total.getRefunded();
            int newCustomers = (int) firstStart.values().stream().filter(t -> YearMonth.from(t).equals(month)).count();
            int churned = (int) lastEnd.values().stream()
                    .filter(t -> YearMonth.from(t).equals(month) && t.isBefore(now)).count();
            int activeAtStart = (int) history.stream().filter(p -> covers(p, start))
                    .map(Purchase::getMerchantId).distinct().count();
            BigDecimal monthMrr = sum(AdminBillingService.currentPerMerchant(
                    history.stream().filter(p -> covers(p, at)).toList()).stream().map(RevenueService::monthly));
            series.add(new Month(MONTH.format(m), gross, refunded, gross.subtract(refunded),
                    total == null ? 0 : total.getPayments(), newCustomers, churned, activeAtStart, monthMrr));
        }

        Month last = series.size() >= 2 ? series.get(series.size() - 2) : null;
        BigDecimal churnRate = last == null || last.activeAtStart() == 0 ? null
                : BigDecimal.valueOf(last.churned()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(last.activeAtStart()), 1, RoundingMode.HALF_UP);

        Map<String, List<Purchase>> byName = paying.stream().collect(Collectors.groupingBy(
                p -> Objects.requireNonNullElse(p.getPackageName(), "Bilinmeyen"), LinkedHashMap::new, Collectors.toList()));
        List<PackageShare> shares = byName.entrySet().stream()
                .map(e -> new PackageShare(e.getKey(), e.getValue().size(), sum(e.getValue().stream().map(RevenueService::monthly))))
                .sorted(Comparator.comparing(PackageShare::mrr).reversed())
                .toList();

        int customers = paying.size();
        return new Summary(
                mrr,
                mrr.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP),
                customers,
                customers == 0 ? BigDecimal.ZERO : mrr.divide(BigDecimal.valueOf(customers), 2, RoundingMode.HALF_UP),
                churnRate,
                dueNext30.setScale(2, RoundingMode.HALF_UP),
                dueCount,
                overdue.setScale(2, RoundingMode.HALF_UP),
                overdueCount,
                "TRY",
                series,
                shares);
    }

    /** What one purchase is worth per month: its renewal charge spread over its billing interval. */
    static BigDecimal monthly(Purchase p) {
        BigDecimal renewal = DunningService.renewalAmount(p);
        if (renewal == null) {
            return BigDecimal.ZERO;
        }
        int interval = p.getBillingIntervalMonths() != null && p.getBillingIntervalMonths() > 0
                ? p.getBillingIntervalMonths()
                : p.getBillingPeriod() != null && p.getBillingPeriod().intervalMonths() > 0 ? p.getBillingPeriod().intervalMonths() : 1;
        return renewal.divide(BigDecimal.valueOf(interval), 2, RoundingMode.HALF_UP);
    }

    private static boolean covers(Purchase p, LocalDateTime t) {
        return p.getStartsAt() != null && !p.getStartsAt().isAfter(t)
                && (p.getExpiresAt() == null || p.getExpiresAt().isAfter(t));
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
