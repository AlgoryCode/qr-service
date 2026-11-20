package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionSegmentRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Device rental report for a branch over whole days: revenue, played time, occupancy per device, busy hours and
 * payment methods. It counts sessions that finished in the period. A session's revenue is split over its devices
 * by played time; played time is placed in the hour it was played.
 */
@Service
@RequiredArgsConstructor
public class DeviceReportService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ConsoleSessionRepository sessionRepository;
    private final DeviceSessionSegmentRepository segmentRepository;
    private final GameConsoleRepository consoleRepository;
    private final ConsoleAccess access;
    private final com.ael.algoryqrservice.qrservice.repository.DeviceMaintenanceLogRepository maintenanceRepository;

    @Transactional(readOnly = true)
    public ConsoleDtos.ReportResponse report(Long branchId, LocalDate fromDay, LocalDate toDay) {
        Branch branch = access.requireBranch(branchId);
        LocalDate today = AppTime.nowLocal().toLocalDate();
        LocalDate first = fromDay == null ? today.minusDays(6) : fromDay;
        LocalDate last = toDay == null || toDay.isBefore(first) ? today : toDay;
        if (last.isAfter(first.plusDays(366))) {
            last = first.plusDays(366);
        }
        LocalDateTime from = first.atStartOfDay();
        LocalDateTime to = last.plusDays(1).atStartOfDay().minusNanos(1);
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime periodEnd = to.isAfter(now) ? now : to;
        long periodMinutes = Math.max(Duration.between(from, periodEnd).toMinutes(), 1);

        List<ConsoleSession> sessions = sessionRepository.findFinishedForBranch(
                branch.getMerchantId(), branch.getId(), from, to);
        List<Long> ids = sessions.stream().map(ConsoleSession::getId).toList();
        Map<Long, List<DeviceSessionSegment>> segments = ids.isEmpty()
                ? Map.of()
                : segmentRepository.findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(DeviceSessionSegment::getSessionId));

        Map<Long, GameConsole> consoles = new LinkedHashMap<>();
        consoleRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(branch.getId(), branch.getMerchantId())
                .forEach(c -> consoles.put(c.getId(), c));
        List<Long> missing = segments.values().stream().flatMap(List::stream).map(DeviceSessionSegment::getConsoleId)
                .filter(id -> !consoles.containsKey(id)).distinct().toList();
        if (!missing.isEmpty()) {
            consoleRepository.findByIdIn(missing).forEach(c -> consoles.put(c.getId(), c));
        }

        Map<Long, DeviceAcc> devices = new LinkedHashMap<>();
        consoles.values().stream().filter(c -> !c.isDeleted()).forEach(c -> devices.put(c.getId(), new DeviceAcc()));
        long[] hourSeconds = new long[24];
        int[] hourStarts = new int[24];
        BigDecimal[] hourRevenue = new BigDecimal[24];
        java.util.Arrays.fill(hourRevenue, BigDecimal.ZERO);
        Map<DevicePaymentMethod, PaymentAcc> payments = new EnumMap<>(DevicePaymentMethod.class);

        Map<String, GameAcc> games = new LinkedHashMap<>();
        BigDecimal timeRevenue = BigDecimal.ZERO;
        BigDecimal itemsRevenue = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        int billedMinutes = 0;
        long playedSecondsTotal = 0;

        for (ConsoleSession session : sessions) {
            BigDecimal amount = nz(session.getAmount());
            revenue = revenue.add(amount);
            timeRevenue = timeRevenue.add(session.getTimeAmount() != null ? session.getTimeAmount() : amount);
            itemsRevenue = itemsRevenue.add(nz(session.getItemsAmount()));
            discounts = discounts.add(nz(session.getDiscountAmount()));
            billedMinutes += session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            hourStarts[session.getStartedAt().getHour()]++;
            hourRevenue[session.getEndedAt().getHour()] = hourRevenue[session.getEndedAt().getHour()].add(amount);
            DevicePaymentMethod method = session.getPaymentMethod() == null ? DevicePaymentMethod.CASH : session.getPaymentMethod();
            PaymentAcc pay = payments.computeIfAbsent(method, m -> new PaymentAcc());
            pay.sessions++;
            pay.amount = pay.amount.add(amount);

            List<DeviceSessionSegment> segs = segments.getOrDefault(session.getId(), List.of());
            long sessionSeconds = 0;
            Map<Long, Long> perDevice = new HashMap<>();
            for (DeviceSessionSegment seg : segs) {
                LocalDateTime end = seg.getEndedAt() == null ? session.getEndedAt() : seg.getEndedAt();
                long seconds = Math.max(Duration.between(seg.getStartedAt(), end).getSeconds(), 0);
                sessionSeconds += seconds;
                perDevice.merge(seg.getConsoleId(), seconds, Long::sum);
                spreadOverHours(seg.getStartedAt(), end, hourSeconds);
            }
            playedSecondsTotal += sessionSeconds;
            if (session.getGameName() != null) {
                GameAcc game = games.computeIfAbsent(session.getGameId() + "|" + session.getGameName(),
                        key -> new GameAcc(session.getGameId(), session.getGameName()));
                game.sessions++;
                game.seconds += sessionSeconds;
            }
            Long lastConsole = segs.isEmpty() ? session.getConsoleId() : segs.get(segs.size() - 1).getConsoleId();
            if (perDevice.isEmpty()) {
                perDevice.put(lastConsole, 0L);
            }
            BigDecimal assigned = BigDecimal.ZERO;
            List<Map.Entry<Long, Long>> entries = new ArrayList<>(perDevice.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<Long, Long> entry = entries.get(i);
                DeviceAcc acc = devices.computeIfAbsent(entry.getKey(), id -> new DeviceAcc());
                acc.seconds += entry.getValue();
                BigDecimal share;
                if (i == entries.size() - 1) {
                    share = amount.subtract(assigned);
                } else {
                    share = sessionSeconds == 0
                            ? BigDecimal.ZERO
                            : amount.multiply(BigDecimal.valueOf(entry.getValue()))
                                    .divide(BigDecimal.valueOf(sessionSeconds), 2, RoundingMode.HALF_UP);
                    assigned = assigned.add(share);
                }
                acc.revenue = acc.revenue.add(share);
            }
            devices.computeIfAbsent(lastConsole, id -> new DeviceAcc()).sessions++;
        }

        List<ConsoleDtos.DeviceReportRow> deviceRows = devices.entrySet().stream()
                .map(e -> {
                    GameConsole c = consoles.get(e.getKey());
                    DeviceAcc acc = e.getValue();
                    int minutes = (int) (acc.seconds / 60);
                    return new ConsoleDtos.DeviceReportRow(
                            e.getKey(),
                            c == null ? null : c.getName(),
                            c == null ? null : c.getDeviceType(),
                            acc.sessions,
                            minutes,
                            acc.revenue.setScale(2, RoundingMode.HALF_UP),
                            percent(acc.seconds / 60.0, periodMinutes)
                    );
                })
                .sorted(Comparator.comparing(ConsoleDtos.DeviceReportRow::revenue).reversed())
                .toList();
        List<ConsoleDtos.HourReportRow> hourRows = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hourRows.add(new ConsoleDtos.HourReportRow(h, (int) (hourSeconds[h] / 60), hourStarts[h],
                    hourRevenue[h].setScale(2, RoundingMode.HALF_UP)));
        }
        List<ConsoleDtos.PaymentReportRow> paymentRows = payments.entrySet().stream()
                .map(e -> new ConsoleDtos.PaymentReportRow(e.getKey(), e.getValue().sessions,
                        e.getValue().amount.setScale(2, RoundingMode.HALF_UP)))
                .toList();
        int count = sessions.size();
        int deviceCount = Math.max((int) devices.keySet().stream().filter(id -> {
            GameConsole c = consoles.get(id);
            return c != null && !c.isDeleted();
        }).count(), 1);
        return new ConsoleDtos.ReportResponse(
                branch.getId(),
                from,
                to,
                count,
                (int) (playedSecondsTotal / 60),
                timeRevenue.setScale(2, RoundingMode.HALF_UP),
                itemsRevenue.setScale(2, RoundingMode.HALF_UP),
                discounts.setScale(2, RoundingMode.HALF_UP),
                revenue.setScale(2, RoundingMode.HALF_UP),
                count == 0 ? BigDecimal.ZERO.setScale(1) : BigDecimal.valueOf(billedMinutes).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP),
                count == 0 ? BigDecimal.ZERO.setScale(2) : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                percent(playedSecondsTotal / 60.0, periodMinutes * deviceCount),
                deviceRows,
                hourRows,
                paymentRows,
                games.values().stream()
                        .sorted(Comparator.comparingLong((GameAcc g) -> g.seconds).reversed())
                        .map(g -> new ConsoleDtos.GameReportRow(g.id, g.name, g.sessions, (int) (g.seconds / 60)))
                        .toList()
        );
    }

    /**
     * Lifetime figures per device of the branch: revenue (split by played time as in the report), maintenance and
     * other costs, and how far the purchase cost is paid back.
     */
    @Transactional(readOnly = true)
    public ConsoleDtos.ProfitabilityResponse profitability(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime monthAgo = now.minusDays(30);
        List<GameConsole> consoles = consoleRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(
                branch.getId(), branch.getMerchantId());
        List<ConsoleSession> sessions = sessionRepository.findFinishedForBranch(
                branch.getMerchantId(), branch.getId(), LocalDateTime.of(2000, 1, 1, 0, 0), now);
        Map<Long, List<DeviceSessionSegment>> segments = sessions.isEmpty()
                ? Map.of()
                : segmentRepository.findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(
                                sessions.stream().map(ConsoleSession::getId).toList()).stream()
                        .collect(Collectors.groupingBy(DeviceSessionSegment::getSessionId));
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, BigDecimal> recent = new HashMap<>();
        Map<Long, Long> seconds = new HashMap<>();
        for (ConsoleSession session : sessions) {
            List<DeviceSessionSegment> segs = segments.getOrDefault(session.getId(), List.of());
            Map<Long, Long> perDevice = new LinkedHashMap<>();
            long total = 0;
            for (DeviceSessionSegment seg : segs) {
                LocalDateTime end = seg.getEndedAt() == null ? session.getEndedAt() : seg.getEndedAt();
                long secs = Math.max(Duration.between(seg.getStartedAt(), end).getSeconds(), 0);
                perDevice.merge(seg.getConsoleId(), secs, Long::sum);
                total += secs;
            }
            if (perDevice.isEmpty()) {
                perDevice.put(session.getConsoleId(), 0L);
            }
            Map<Long, BigDecimal> shares = split(nz(session.getAmount()), perDevice, total);
            boolean isRecent = !session.getEndedAt().isBefore(monthAgo);
            shares.forEach((consoleId, share) -> {
                revenue.merge(consoleId, share, BigDecimal::add);
                if (isRecent) {
                    recent.merge(consoleId, share, BigDecimal::add);
                }
            });
            perDevice.forEach((consoleId, secs) -> seconds.merge(consoleId, secs, Long::sum));
        }
        Map<Long, BigDecimal> costs = new HashMap<>();
        maintenanceRepository.findByBranchIdAndMerchantIdAndDeletedFalse(branch.getId(), branch.getMerchantId())
                .forEach(log -> {
                    if (log.getCost() != null) {
                        costs.merge(log.getConsoleId(), log.getCost(), BigDecimal::add);
                    }
                });

        BigDecimal sumPurchase = BigDecimal.ZERO;
        BigDecimal sumRevenue = BigDecimal.ZERO;
        BigDecimal sumCost = BigDecimal.ZERO;
        List<ConsoleDtos.DeviceProfitRow> rows = new ArrayList<>();
        for (GameConsole console : consoles) {
            BigDecimal rev = revenue.getOrDefault(console.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = costs.getOrDefault(console.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal last30 = recent.getOrDefault(console.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal purchase = console.getPurchaseCost();
            BigDecimal earned = rev.subtract(cost);
            BigDecimal net = purchase == null ? earned : earned.subtract(purchase);
            BigDecimal payback = null;
            Integer days = null;
            boolean paidBack = false;
            if (purchase != null && purchase.signum() > 0) {
                payback = earned.max(BigDecimal.ZERO).multiply(HUNDRED).divide(purchase, 1, RoundingMode.HALF_UP);
                paidBack = earned.compareTo(purchase) >= 0;
                if (paidBack) {
                    days = 0;
                } else if (last30.signum() > 0) {
                    BigDecimal perDay = last30.divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);
                    days = purchase.subtract(earned).divide(perDay, 0, RoundingMode.CEILING).min(BigDecimal.valueOf(36500)).intValue();
                }
            } else if (purchase != null) {
                paidBack = true;
                days = 0;
            }
            rows.add(new ConsoleDtos.DeviceProfitRow(
                    console.getId(),
                    console.getName(),
                    console.getDeviceType(),
                    purchase,
                    console.getPurchasedOn(),
                    (int) (seconds.getOrDefault(console.getId(), 0L) / 60),
                    rev,
                    cost,
                    net.setScale(2, RoundingMode.HALF_UP),
                    payback,
                    paidBack,
                    last30,
                    days
            ));
            sumPurchase = sumPurchase.add(purchase == null ? BigDecimal.ZERO : purchase);
            sumRevenue = sumRevenue.add(rev);
            sumCost = sumCost.add(cost);
        }
        return new ConsoleDtos.ProfitabilityResponse(
                branch.getId(),
                sumPurchase.setScale(2, RoundingMode.HALF_UP),
                sumRevenue.setScale(2, RoundingMode.HALF_UP),
                sumCost.setScale(2, RoundingMode.HALF_UP),
                sumRevenue.subtract(sumCost).subtract(sumPurchase).setScale(2, RoundingMode.HALF_UP),
                rows
        );
    }

    /** Splits {@code amount} by seconds; the last device takes the rounding remainder. */
    static Map<Long, BigDecimal> split(BigDecimal amount, Map<Long, Long> seconds, long total) {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        List<Map.Entry<Long, Long>> entries = new ArrayList<>(seconds.entrySet());
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<Long, Long> entry = entries.get(i);
            BigDecimal share;
            if (i == entries.size() - 1) {
                share = amount.subtract(assigned);
            } else {
                share = total == 0
                        ? BigDecimal.ZERO
                        : amount.multiply(BigDecimal.valueOf(entry.getValue()))
                                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                assigned = assigned.add(share);
            }
            shares.put(entry.getKey(), share);
        }
        return shares;
    }

    /** Adds the seconds of [start, end) to the hour of day each part falls in. */
    static void spreadOverHours(LocalDateTime start, LocalDateTime end, long[] hourSeconds) {
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            LocalDateTime chunkEnd = nextHour.isBefore(end) ? nextHour : end;
            hourSeconds[cursor.getHour()] += Duration.between(cursor, chunkEnd).getSeconds();
            cursor = chunkEnd;
        }
    }

    private static BigDecimal percent(double part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP)
                .min(HUNDRED.setScale(1));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class DeviceAcc {
        int sessions;
        long seconds;
        BigDecimal revenue = BigDecimal.ZERO;
    }

    private static final class GameAcc {
        final Long id;
        final String name;
        int sessions;
        long seconds;

        GameAcc(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class PaymentAcc {
        int sessions;
        BigDecimal amount = BigDecimal.ZERO;
    }
}
