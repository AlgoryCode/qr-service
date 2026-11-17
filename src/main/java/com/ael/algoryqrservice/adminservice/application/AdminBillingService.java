package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.AccountPage;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.AccountRow;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Sort;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Summary;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import com.ael.algoryqrservice.qrservice.billing.service.RecurringAddonBilling;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who has paid, whose payment is coming up and who owes money, one row per paying merchant. Built
 * from each merchant's current paid package purchase; free, trial and operator-granted packages are
 * not billing relationships and are left out.
 */
@Service
@RequiredArgsConstructor
public class AdminBillingService {

    static final int DEFAULT_DUE_WITHIN_DAYS = 7;
    static final int MAX_DUE_WITHIN_DAYS = 90;
    /** Lapsed packages older than this are history, not follow-up work. */
    public static final int LAPSED_LOOKBACK_DAYS = 90;
    static final int MAX_PAGE_SIZE = 100;

    private static final Set<SubscriptionStatus> IN_ARREARS =
            EnumSet.of(SubscriptionStatus.PAST_DUE, SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.SUSPENDED);

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final CollectionCaseRepository collectionCaseRepository;

    @Transactional(readOnly = true)
    public AccountPage list(String standing, String q, String paymentStyle, Integer dueWithinDays,
                            String sort, int page, int size) {
        return list(standing, null, q, paymentStyle, dueWithinDays, sort, page, size);
    }

    /**
     * @param collection {@code OPEN} (any open case), {@code NONE} (no open case), a
     *                   {@link CollectionStatus} name, or null for everyone
     */
    @Transactional(readOnly = true)
    public AccountPage list(String standing, String collection, String q, String paymentStyle, Integer dueWithinDays,
                            String sort, int page, int size) {
        Standing wanted = parse(Standing.class, standing, "standing");
        Function<CollectionCase, Boolean> collectionFilter = collectionFilter(collection);
        PaymentStyle style = parse(PaymentStyle.class, paymentStyle, "paymentStyle");
        Sort order = parse(Sort.class, sort, "sort");
        int window = dueWithinDays == null ? DEFAULT_DUE_WITHIN_DAYS : dueWithinDays;
        if (window < 1 || window > MAX_DUE_WITHIN_DAYS) {
            throw new BadRequestException("dueWithinDays 1 ile " + MAX_DUE_WITHIN_DAYS + " arasında olmalı");
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);
        LocalDateTime now = AppTime.nowLocal();

        Map<Long, CollectionCase> cases = collectionCaseRepository.findByClosedAtIsNull().stream()
                .collect(Collectors.toMap(CollectionCase::getMerchantId, Function.identity(), (x, y) -> x));
        List<Purchase> current = withOpenCases(currentPerMerchant(
                purchaseRepository.findBillingStandingCandidates(now.minusDays(LAPSED_LOOKBACK_DAYS))), cases);
        Map<Long, User> users = userRepository.findAllById(
                        current.stream().map(Purchase::getMerchantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<AccountRow> matching = current.stream()
                .filter(p -> style == null || p.getPaymentStyle() == style)
                .map(p -> row(p, users.get(p.getMerchantId()), cases.get(p.getMerchantId()), now, window))
                .filter(row -> needle.isEmpty() || matches(row, needle))
                .toList();

        Summary summary = summarize(matching, window);
        List<AccountRow> filtered = matching.stream()
                .filter(row -> wanted == null || row.standing() == wanted)
                .filter(row -> collectionFilter.apply(cases.get(row.merchantId())))
                .sorted(comparator(order == null ? Sort.DUE_ASC : order))
                .toList();

        int from = Math.min(pageIndex * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        int totalPages = (filtered.size() + pageSize - 1) / pageSize;
        return new AccountPage(filtered.subList(from, to), summary, pageIndex, pageSize,
                filtered.size(), totalPages, to < filtered.size());
    }

    /** Where a paid package purchase stands at {@code now}. */
    public static Standing standing(Purchase purchase, LocalDateTime now, int dueWithinDays) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE) {
            return Standing.LAPSED;
        }
        if (purchase.getSubscriptionStatus() != null && IN_ARREARS.contains(purchase.getSubscriptionStatus())) {
            return Standing.OVERDUE;
        }
        LocalDateTime end = purchase.getExpiresAt();
        if (end != null && end.isBefore(now)) {
            // Still active past its end: a renewal that never arrived, unless it was never going to renew.
            return RecurringAddonBilling.renewsWithHost(purchase) ? Standing.OVERDUE : Standing.LAPSED;
        }
        if (end != null && end.isBefore(now.plusDays(dueWithinDays))) {
            return Standing.DUE_SOON;
        }
        return Standing.PAID;
    }

    /** One purchase per merchant: the active package if there is one, else the most recently lapsed. */
    public static List<Purchase> currentPerMerchant(Collection<Purchase> candidates) {
        Comparator<Purchase> preference = Comparator
                .comparing((Purchase p) -> p.getStatus() == PurchaseStatus.ACTIVE)
                .thenComparing(Purchase::getExpiresAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Purchase::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return candidates.stream()
                .collect(Collectors.groupingBy(Purchase::getMerchantId))
                .values().stream()
                .map(list -> list.stream().max(preference).orElseThrow())
                .toList();
    }

    /** Merchants with an open case stay listed even after their package left the lapsed window. */
    private List<Purchase> withOpenCases(List<Purchase> current, Map<Long, CollectionCase> cases) {
        Set<Long> listed = current.stream().map(Purchase::getMerchantId).collect(Collectors.toSet());
        List<Long> missing = cases.values().stream()
                .filter(c -> !listed.contains(c.getMerchantId()) && c.getPurchaseId() != null)
                .map(CollectionCase::getPurchaseId)
                .toList();
        if (missing.isEmpty()) {
            return current;
        }
        return Stream.concat(current.stream(), purchaseRepository.findAllById(missing).stream()).toList();
    }

    private static Function<CollectionCase, Boolean> collectionFilter(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return c -> true;
        }
        if ("OPEN".equalsIgnoreCase(raw)) {
            return Objects::nonNull;
        }
        if ("NONE".equalsIgnoreCase(raw)) {
            return Objects::isNull;
        }
        CollectionStatus status = parse(CollectionStatus.class, raw, "collection");
        return c -> c != null && c.getStatus() == status;
    }

    private static AccountRow row(Purchase p, User user, CollectionCase collectionCase, LocalDateTime now, int window) {
        Standing standing = standing(p, now, window);
        boolean autoRenew = RecurringAddonBilling.renewsWithHost(p);
        BigDecimal renewal = RecurringAddonBilling.renewalAmount(p);
        Long daysUntilDue = p.getExpiresAt() == null ? null : daysBetween(now, p.getExpiresAt());
        return new AccountRow(
                p.getMerchantId(),
                user == null ? null : fullName(user),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(),
                p.getId(),
                p.getPackageCode(),
                p.getPackageName(),
                p.getBillingPeriod(),
                p.getPaymentStyle(),
                autoRenew,
                p.isCancelAtPeriodEnd(),
                standing,
                p.getSubscriptionStatus(),
                renewal,
                standing == Standing.OVERDUE ? renewal : null,
                p.getCurrency(),
                p.getCurrentPeriodPaidAt() != null ? p.getCurrentPeriodPaidAt() : p.getPurchasedAt(),
                p.getExpiresAt(),
                daysUntilDue,
                p.getSubscriptionGraceEndsAt(),
                p.getCardBrand(),
                p.getCardLastFour(),
                collectionCase == null ? null : collectionCase.getId(),
                collectionCase == null ? null : collectionCase.getStatus(),
                collectionCase == null ? null : collectionCase.getStatusAt()
        );
    }

    /** Whole days from now until {@code end}, rounded toward the due date; negative once it has passed. */
    static long daysBetween(LocalDateTime now, LocalDateTime end) {
        long hours = Duration.between(now, end).toHours();
        return hours >= 0 ? (hours + 23) / 24 : -((-hours + 23) / 24);
    }

    private static Summary summarize(List<AccountRow> rows, int window) {
        Map<Standing, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(AccountRow::standing, Collectors.counting()));
        return new Summary(
                counts.getOrDefault(Standing.PAID, 0L),
                counts.getOrDefault(Standing.DUE_SOON, 0L),
                counts.getOrDefault(Standing.OVERDUE, 0L),
                counts.getOrDefault(Standing.LAPSED, 0L),
                sum(rows.stream().filter(r -> r.standing() == Standing.DUE_SOON && r.autoRenew())
                        .map(AccountRow::renewalAmount)),
                sum(rows.stream().map(AccountRow::outstandingAmount)),
                window,
                rows.stream().filter(r -> r.collectionCaseId() != null).count()
        );
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Comparator<AccountRow> comparator(Sort sort) {
        Comparator<AccountRow> byDue = Comparator.comparing(AccountRow::periodEndsAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<AccountRow> chosen = switch (sort) {
            case DUE_ASC -> byDue;
            case DUE_DESC -> Comparator.comparing(AccountRow::periodEndsAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case AMOUNT_DESC -> Comparator.comparing(AccountRow::renewalAmount,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case LAST_PAID_DESC -> Comparator.comparing(AccountRow::lastPaidAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return chosen.thenComparing(AccountRow::merchantId);
    }

    private static boolean matches(AccountRow row, String needle) {
        return Stream.of(row.merchantName(), row.email(), row.phone(), row.packageName(), row.packageCode(),
                        String.valueOf(row.merchantId()))
                .filter(Objects::nonNull)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static String fullName(User user) {
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return name.isBlank() ? null : name;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Geçersiz " + field + ": " + raw);
        }
    }
}
