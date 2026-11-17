package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.PlanVersion;
import com.ael.algoryqrservice.qrservice.model.PlanVersionItem;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps one immutable version per distinct shape of a package (its lines and list prices). Campaign
 * discounts are not part of the shape: they are temporary and the purchase records what was charged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanVersionService {

    public record VersionLine(Long productId, String productCode, String productName, String featureCode,
                              Integer quantity, boolean unlimited) {
    }

    public record VersionSummary(Long id, Integer version, boolean current, BigDecimal price, BigDecimal yearlyPrice,
                                 String currency, Integer validityDays, String note,
                                 String createdBy, LocalDateTime createdAt, long activeSubscribers,
                                 List<VersionLine> lines) {
    }

    private final PlanVersionRepository versions;
    private final PlanPackageRepository packages;

    /** Opens a new version when the package's lines or list prices differ from its current one. */
    @Transactional
    public PlanVersion snapshot(PlanPackage pkg, String note) {
        Optional<PlanVersion> current = Optional.ofNullable(pkg.getCurrentVersionId())
                .flatMap(versions::findByIdWithItems);
        if (current.isPresent() && sameShape(current.get(), pkg)) {
            return current.get();
        }
        int next = versions.findFirstByPackageIdOrderByVersionDesc(pkg.getId())
                .map(version -> version.getVersion() + 1)
                .orElse(1);
        PlanVersion version = PlanVersion.builder()
                .packageId(pkg.getId())
                .version(next)
                .price(pkg.getPrice())
                .yearlyPrice(pkg.getYearlyPrice())
                .monthlyDiscount(orZero(pkg.getMonthlyDiscount()))
                .yearlyDiscount(orZero(pkg.getYearlyDiscount()))
                .currency(pkg.getCurrency() == null ? "TRY" : pkg.getCurrency())
                .validityDays(pkg.getValidityDays() == null ? 30 : pkg.getValidityDays())
                .note(note)
                .createdBy(actor())
                .build();
        for (PlanPackageItem item : pkg.getItems()) {
            version.getItems().add(PlanVersionItem.builder()
                    .planVersion(version)
                    .product(item.getProduct())
                    .quantity(item.getQuantity() == null ? 0 : item.getQuantity())
                    .unlimited(item.isUnlimited())
                    .build());
        }
        PlanVersion saved = versions.save(version);
        pkg.setCurrentVersionId(saved.getId());
        packages.save(pkg);
        log.info("plan_version_created package={} version={} lines={}", pkg.getCode(), next, saved.getItems().size());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<VersionSummary> list(Long packageId) {
        PlanPackage pkg = packages.findById(packageId).orElseThrow(() -> new NotFoundException("Paket bulunamadı"));
        Map<Long, Long> subscribers = new HashMap<>();
        for (Object[] row : versions.countActivePurchasesByVersion(packageId)) {
            subscribers.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return versions.findByPackageIdOrderByVersionDesc(packageId).stream()
                .map(version -> versions.findByIdWithItems(version.getId()).orElse(version))
                .map(version -> new VersionSummary(
                        version.getId(), version.getVersion(), Objects.equals(version.getId(), pkg.getCurrentVersionId()),
                        version.getPrice(), version.getYearlyPrice(), version.getCurrency(),
                        version.getValidityDays(), version.getNote(), version.getCreatedBy(), version.getCreatedAt(),
                        subscribers.getOrDefault(version.getId(), 0L),
                        version.getItems().stream()
                                .sorted(Comparator.comparing(item -> item.getProduct().getCode()))
                                .map(item -> new VersionLine(item.getProduct().getId(), item.getProduct().getCode(),
                                        item.getProduct().getName(), item.getProduct().getFeatureCode(),
                                        item.getQuantity(), item.isUnlimited()))
                                .toList()))
                .toList();
    }

    /**
     * Moves the package's live subscribers to another of its versions (all of them, or only those on
     * {@code fromVersionId}). Their rights change at the next renewal; the price they pay does not.
     */
    @Transactional
    public int moveSubscribers(Long packageId, Long fromVersionId, Long toVersionId) {
        PlanVersion target = versions.findById(toVersionId)
                .filter(version -> version.getPackageId().equals(packageId))
                .orElseThrow(() -> new BadRequestException("Hedef sürüm bu pakete ait değil"));
        int moved = versions.movePurchases(packageId, fromVersionId, target.getId());
        log.info("plan_version_subscribers_moved package={} from={} to={} count={}",
                packageId, fromVersionId, target.getVersion(), moved);
        return moved;
    }

    private static boolean sameShape(PlanVersion version, PlanPackage pkg) {
        return sameMoney(version.getPrice(), pkg.getPrice())
                && sameMoney(version.getYearlyPrice(), pkg.getYearlyPrice())
                && Objects.equals(version.getCurrency(), pkg.getCurrency())
                && Objects.equals(version.getValidityDays(), pkg.getValidityDays())
                && lines(version).equals(linesOf(pkg));
    }

    private static Set<String> lines(PlanVersion version) {
        return version.getItems().stream()
                .map(item -> line(item.getProduct().getId(), item.getQuantity(), item.isUnlimited()))
                .collect(Collectors.toSet());
    }

    private static Set<String> linesOf(PlanPackage pkg) {
        return pkg.getItems().stream()
                .map(item -> line(item.getProduct().getId(), item.getQuantity(), item.isUnlimited()))
                .collect(Collectors.toSet());
    }

    private static String line(Long productId, Integer quantity, boolean unlimited) {
        return productId + ":" + (unlimited ? "*" : String.valueOf(quantity == null ? 0 : quantity));
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
