package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.CatalogSchedule;
import com.ael.algoryqrservice.qrservice.model.CatalogSchedule.Status;
import com.ael.algoryqrservice.qrservice.model.CatalogSchedule.TargetType;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.repository.CatalogScheduleRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Price changes and discount campaigns that start (and optionally end) on their own. Each
 * change runs in the catalog service's own transaction so one bad row cannot block the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogScheduleService {

    private static final EnumSet<Status> OPEN = EnumSet.of(Status.SCHEDULED, Status.ACTIVE);

    private final CatalogScheduleRepository repository;
    private final AdminCatalogService catalog;
    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;

    public record Request(
            @NotNull TargetType targetType,
            @NotNull Long targetId,
            @NotBlank @Size(max = 120) String label,
            @DecimalMin("0") BigDecimal unitPrice,
            @DecimalMin("0") BigDecimal monthlyDiscount,
            @DecimalMin("0") BigDecimal yearlyDiscount,
            @NotNull LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
    }

    public record View(Long id, TargetType targetType, Long targetId, String targetName, String label,
                       BigDecimal unitPrice, BigDecimal monthlyDiscount, BigDecimal yearlyDiscount,
                       BigDecimal currentListPrice, LocalDateTime startsAt, LocalDateTime endsAt, Status status,
                       BigDecimal prevUnitPrice, BigDecimal prevMonthlyDiscount, BigDecimal prevYearlyDiscount,
                       String lastError, String createdBy, LocalDateTime createdAt, LocalDateTime appliedAt,
                       LocalDateTime endedAt) {
    }

    public record ActiveCampaign(String label, LocalDateTime endsAt) {
    }

    public List<View> list() {
        List<CatalogSchedule> rows = repository.findAllByOrderByStartsAtDesc();
        Map<Long, Product> products = productRepository.findAllById(rows.stream()
                        .filter(r -> r.getTargetType() == TargetType.PRODUCT).map(CatalogSchedule::getTargetId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, PlanPackage> packages = planPackageRepository.findAllById(rows.stream()
                        .filter(r -> r.getTargetType() == TargetType.PACKAGE).map(CatalogSchedule::getTargetId).toList())
                .stream().collect(Collectors.toMap(PlanPackage::getId, Function.identity()));
        return rows.stream().map(r -> view(r, products, packages)).toList();
    }

    public View create(Request request, String actor) {
        LocalDateTime now = AppTime.nowLocal();
        validate(request, now);
        checkTarget(request);
        List<CatalogSchedule> open = repository.findByTargetTypeAndTargetIdAndStatusIn(
                request.targetType(), request.targetId(), OPEN);
        open.stream().filter(o -> o.overlaps(request.startsAt(), request.endsAt())).findFirst().ifPresent(o -> {
            throw new BadRequestException("Bu tarihlerde aynı hedef için başka bir plan var: " + o.getLabel());
        });
        CatalogSchedule saved = repository.save(CatalogSchedule.builder()
                .targetType(request.targetType())
                .targetId(request.targetId())
                .label(request.label().trim())
                .unitPrice(request.targetType() == TargetType.PRODUCT ? request.unitPrice() : null)
                .monthlyDiscount(request.targetType() == TargetType.PACKAGE ? request.monthlyDiscount() : null)
                .yearlyDiscount(request.targetType() == TargetType.PACKAGE ? request.yearlyDiscount() : null)
                .startsAt(request.startsAt().isBefore(now) ? now : request.startsAt())
                .endsAt(request.endsAt())
                .status(Status.SCHEDULED)
                .createdBy(actor)
                .createdAt(now)
                .build());
        if (!saved.getStartsAt().isAfter(now)) {
            start(saved, now);
        }
        return list().stream().filter(v -> v.id().equals(saved.getId())).findFirst().orElseThrow();
    }

    /** Scheduled: dropped. Running campaign: ended now and the old values restored. */
    public View cancel(Long id) {
        CatalogSchedule schedule = repository.findById(id).orElseThrow(() -> new NotFoundException("Plan bulunamadı"));
        LocalDateTime now = AppTime.nowLocal();
        switch (schedule.getStatus()) {
            case SCHEDULED -> {
                schedule.setStatus(Status.CANCELLED);
                schedule.setEndedAt(now);
                repository.save(schedule);
            }
            case ACTIVE -> end(schedule, now);
            default -> throw new BadRequestException("Bu plan zaten tamamlanmış");
        }
        return list().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    /** Ends finished windows first, then starts due ones; returns how many rows changed. */
    public int runDue() {
        LocalDateTime now = AppTime.nowLocal();
        int changed = 0;
        for (CatalogSchedule s : repository.findByStatusAndEndsAtLessThanEqualOrderByEndsAtAsc(Status.ACTIVE, now)) {
            end(s, now);
            changed++;
        }
        for (CatalogSchedule s : repository.findByStatusAndStartsAtLessThanEqualOrderByStartsAtAsc(Status.SCHEDULED, now)) {
            start(s, now);
            changed++;
        }
        return changed;
    }

    /** Package id → the discount campaign running on it now, for the public price list. */
    public Map<Long, ActiveCampaign> activeCampaigns() {
        return repository.findByTargetTypeAndStatus(TargetType.PACKAGE, Status.ACTIVE).stream()
                .filter(s -> s.getEndsAt() != null)
                .collect(Collectors.toMap(CatalogSchedule::getTargetId,
                        s -> new ActiveCampaign(s.getLabel(), s.getEndsAt()), (a, b) -> a));
    }

    private void start(CatalogSchedule s, LocalDateTime now) {
        try {
            if (s.getTargetType() == TargetType.PRODUCT) {
                s.setPrevUnitPrice(catalog.changeUnitPrice(s.getTargetId(), s.getUnitPrice()));
            } else {
                BigDecimal[] previous = catalog.changePackageDiscounts(s.getTargetId(), s.getMonthlyDiscount(), s.getYearlyDiscount());
                s.setPrevMonthlyDiscount(s.getMonthlyDiscount() == null ? null : previous[0]);
                s.setPrevYearlyDiscount(s.getYearlyDiscount() == null ? null : previous[1]);
            }
            s.setAppliedAt(now);
            s.setLastError(null);
            if (s.getEndsAt() == null) {
                s.setStatus(Status.DONE);
                s.setEndedAt(now);
            } else {
                s.setStatus(Status.ACTIVE);
            }
            log.info("catalog_schedule started id={} label={}", s.getId(), s.getLabel());
        } catch (RuntimeException ex) {
            s.setStatus(Status.FAILED);
            s.setLastError(truncate(ex.getMessage()));
            log.warn("catalog_schedule start_failed id={} reason={}", s.getId(), ex.getMessage());
        }
        repository.save(s);
    }

    private void end(CatalogSchedule s, LocalDateTime now) {
        try {
            if (s.getTargetType() == TargetType.PRODUCT) {
                if (s.getPrevUnitPrice() != null) {
                    catalog.changeUnitPrice(s.getTargetId(), s.getPrevUnitPrice());
                }
            } else {
                catalog.changePackageDiscounts(s.getTargetId(), s.getPrevMonthlyDiscount(), s.getPrevYearlyDiscount());
            }
            s.setStatus(Status.DONE);
            s.setEndedAt(now);
            s.setLastError(null);
            log.info("catalog_schedule ended id={} label={}", s.getId(), s.getLabel());
        } catch (RuntimeException ex) {
            // Stays ACTIVE so the next run tries again; the error is visible in the panel.
            s.setLastError(truncate("Geri alınamadı: " + ex.getMessage()));
            log.warn("catalog_schedule end_failed id={} reason={}", s.getId(), ex.getMessage());
        }
        repository.save(s);
    }

    static void validate(Request r, LocalDateTime now) {
        if (r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) {
            throw new BadRequestException("Bitiş, başlangıçtan sonra olmalı");
        }
        if (r.endsAt() != null && !r.endsAt().isAfter(now)) {
            throw new BadRequestException("Bitiş geçmişte olamaz");
        }
        if (r.targetType() == TargetType.PRODUCT) {
            if (r.unitPrice() == null) {
                throw new BadRequestException("Yeni birim fiyatı girin");
            }
        } else if (r.monthlyDiscount() == null && r.yearlyDiscount() == null) {
            throw new BadRequestException("Aylık ya da yıllık indirim girin");
        }
    }

    /** Rejects up front what would fail at start time against today's prices. */
    private void checkTarget(Request r) {
        if (r.targetType() == TargetType.PRODUCT) {
            productRepository.findById(r.targetId()).orElseThrow(() -> new BadRequestException("Ürün bulunamadı"));
            return;
        }
        PlanPackage p = planPackageRepository.findById(r.targetId())
                .orElseThrow(() -> new BadRequestException("Paket bulunamadı"));
        if (r.monthlyDiscount() != null && r.monthlyDiscount().compareTo(p.getPrice()) >= 0) {
            throw new BadRequestException("Aylık indirim paket fiyatından (" + p.getPrice() + ") küçük olmalı");
        }
        if (r.yearlyDiscount() != null) {
            if (p.getYearlyPrice() == null) {
                throw new BadRequestException("Bu paketin yıllık fiyatı yok; yıllık indirim uygulanamaz");
            }
            if (r.yearlyDiscount().compareTo(p.getYearlyPrice()) >= 0) {
                throw new BadRequestException("Yıllık indirim yıllık fiyattan (" + p.getYearlyPrice() + ") küçük olmalı");
            }
        }
    }

    private View view(CatalogSchedule r, Map<Long, Product> products, Map<Long, PlanPackage> packages) {
        String name;
        BigDecimal listPrice;
        if (r.getTargetType() == TargetType.PRODUCT) {
            Product p = products.get(r.getTargetId());
            name = p == null ? "Silinmiş ürün #" + r.getTargetId() : p.getName();
            listPrice = p == null ? null : p.getUnitPrice();
        } else {
            PlanPackage p = packages.get(r.getTargetId());
            name = p == null ? "Silinmiş paket #" + r.getTargetId() : p.getName();
            listPrice = p == null ? null : p.getPrice();
        }
        return new View(r.getId(), r.getTargetType(), r.getTargetId(), name, r.getLabel(), r.getUnitPrice(),
                r.getMonthlyDiscount(), r.getYearlyDiscount(), listPrice, r.getStartsAt(), r.getEndsAt(), r.getStatus(),
                r.getPrevUnitPrice(), r.getPrevMonthlyDiscount(), r.getPrevYearlyDiscount(), r.getLastError(),
                r.getCreatedBy(), r.getCreatedAt(), r.getAppliedAt(), r.getEndedAt());
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Bilinmeyen hata";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
