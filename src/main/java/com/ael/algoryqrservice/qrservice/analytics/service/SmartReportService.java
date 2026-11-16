package com.ael.algoryqrservice.qrservice.analytics.service;

import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.qrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.qrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.qrservice.model.SmartReportResult;
import com.ael.algoryqrservice.qrservice.analytics.dto.AnalyticsDtos;
import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportModelDtos;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.qrservice.repository.SmartReportFeedbackRepository;
import com.ael.algoryqrservice.qrservice.repository.SmartReportResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.analytics.service.AnalyticsService;
import com.ael.algoryqrservice.qrservice.analytics.service.BatchReportService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartReportService {

    private final AnalyticsService analyticsService;
    private final SmartReportModelInputBuilder smartReportModelInputBuilder;
    private final BatchReportService batchReportService;
    private final SmartReportQuotaProperties quotaProperties;
    private final SmartReportEventRepository smartReportEventRepository;
    private final SmartReportResultRepository smartReportResultRepository;
    private final SmartReportFeedbackRepository smartReportFeedbackRepository;
    private final FulfillmentServiceClient fulfillmentClient;
    private final ProductUsageGateway productUsageGateway;
    private final ProductRepository productRepository;
    private final SmartReportCompletionNotifier smartReportCompletionNotifier;
    private final ObjectMapper objectMapper;

    @Transactional
    public SmartReportDtos.SmartReportAccepted enqueueForBranch(
            Long branchId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        return enqueue(branchId, null, ownerId, from, to, locale, options, ReportTarget.BRANCH);
    }

    /**
     * Queues a smart report over every branch and QR menu of the merchant. The
     * merchant's detailed reports are aggregated and sent to the ai-service batch API.
     */
    @Transactional
    public SmartReportDtos.SmartReportAccepted enqueueForMerchant(
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        return enqueue(null, null, ownerId, from, to, locale, options, ReportTarget.MERCHANT);
    }

    @Transactional
    public SmartReportDtos.SmartReportAccepted enqueue(
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        return enqueue(null, menuId, ownerId, from, to, locale, options, ReportTarget.MENU);
    }

    private SmartReportDtos.SmartReportAccepted enqueue(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options,
            ReportTarget target
    ) {
        boolean merchantWide = target == ReportTarget.MERCHANT;
        boolean branchOnly = target == ReportTarget.BRANCH || merchantWide;
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        if (target == ReportTarget.BRANCH && branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId is required");
        }
        if (target == ReportTarget.MENU && menuId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "menuId is required");
        }

        String creditSource = assertQuotaAvailable(ownerId);

        AnalyticsDtos.MenuAnalyticsReportResponse report;
        AnalyticsDtos.MenuRevenueReportResponse revenue;
        AnalyticsDtos.MerchantStaffPerformanceReportResponse waiter;
        if (merchantWide) {
            AnalyticsDtos.MerchantDetailedReportResponse detailed =
                    analyticsService.getMerchantDetailedReport(ownerId, from, to);
            report = detailed.visits();
            revenue = detailed.revenue();
            waiter = detailed.waiter();
        } else if (branchOnly) {
            report = analyticsService.getBranchReport(branchId, null, ownerId, from, to);
            revenue = analyticsService.getBranchRevenueReport(branchId, null, ownerId, from, to);
            waiter = analyticsService.getBranchWaiterPerformanceReport(branchId, null, ownerId, from, to);
        } else {
            report = analyticsService.getMenuReport(menuId, ownerId, from, to);
            revenue = analyticsService.getMenuRevenueReport(menuId, ownerId, from, to);
            waiter = analyticsService.getMerchantStaffPerformanceReport(menuId, ownerId, from, to);
        }

        UUID processId = UUID.randomUUID();
        String resolvedLocale = locale == null || locale.isBlank() ? "tr" : locale.trim();
        Long resolvedBranchId = merchantWide
                ? null
                : report.branchId() != null ? report.branchId() : branchId;
        String branchName = merchantWide ? AnalyticsService.MERCHANT_WIDE_SCOPE_NAME : report.branchName();
        Long resolvedMenuId = branchOnly ? null : report.menuId();
        String menuName = branchOnly ? null : report.menuName();
        if (!branchOnly && (menuName == null || menuName.isBlank())) {
            menuName = resolvedMenuId != null ? "Menu #" + resolvedMenuId : "Menu";
        }

        AnalyticsDtos.MenuAnalyticsReportResponse aiVisits = branchOnly
                ? stripMenuIdentity(report, resolvedBranchId, branchName)
                : withMenuIdentity(report, resolvedMenuId, menuName);
        AnalyticsDtos.MenuRevenueReportResponse aiRevenue = branchOnly
                ? stripMenuIdentity(revenue, resolvedBranchId, branchName)
                : revenue;
        AnalyticsDtos.MerchantStaffPerformanceReportResponse aiWaiter = branchOnly
                ? stripMenuIdentity(waiter, resolvedBranchId, branchName)
                : waiter;

        smartReportEventRepository.save(SmartReportEvent.builder()
                .processId(processId)
                .merchantId(ownerId)
                .menuId(resolvedMenuId)
                .menuName(menuName)
                .branchId(resolvedBranchId)
                .branchName(branchName)
                .fromDate(from)
                .toDate(to)
                .locale(resolvedLocale)
                .status(SmartReportEvent.STATUS_QUEUED)
                .creditSource(creditSource)
                .build());


        Map<String, Object> optionsMap = SmartReportDtos.toOptionsMap(options);
        if (branchOnly) {
            Map<String, Object> branchOptions = optionsMap == null ? new HashMap<>() : new HashMap<>(optionsMap);
            branchOptions.putIfAbsent(
                    "focusAreas",
                    merchantWide
                            ? List.of("tüm şubelerin genel performansı", "kanal kıyası", "ciro ve ürün satışları", "personel performansı")
                            : List.of("şube siparişleri", "kanal kıyası", "ciro ve ürün satışları")
            );
            optionsMap = branchOptions;
        }

        SmartReportModelDtos.SmartReportModelInput input = smartReportModelInputBuilder.build(
                aiVisits,
                aiRevenue,
                aiWaiter,
                resolvedLocale,
                optionsMap
        );

        Map<String, Object> payload = objectMapper.convertValue(input, new TypeReference<>() {
        });
        batchReportService.createSubmittedBatch(
                ownerId,
                List.of(new BatchReportService.BatchReportItemDraft(
                        processId,
                        payload,
                        resolvedBranchId,
                        branchName,
                        resolvedMenuId,
                        menuName,
                        from,
                        to,
                        resolvedLocale
                ))
        );

        log.info(
                "Smart report batch submitted. processId={} branchId={} menuId={} target={} merchantId={} from={} to={}",
                processId,
                resolvedBranchId,
                resolvedMenuId,
                target,
                ownerId,
                from,
                to
        );
        return new SmartReportDtos.SmartReportAccepted(processId, SmartReportEvent.STATUS_QUEUED);
    }

    private enum ReportTarget {
        MENU,
        BRANCH,
        MERCHANT
    }

    private static AnalyticsDtos.MenuAnalyticsReportResponse stripMenuIdentity(
            AnalyticsDtos.MenuAnalyticsReportResponse report,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                null,
                null,
                branchId,
                branchName,
                report.from(),
                report.to(),
                report.kpis(),
                report.daily(),
                report.hourly(),
                report.devices(),
                report.topProducts(),
                report.topCategories(),
                report.categoryProductTree(),
                report.sampleJourneys(),
                report.funnel(),
                report.feedback()
        );
    }

    private static AnalyticsDtos.MenuAnalyticsReportResponse withMenuIdentity(
            AnalyticsDtos.MenuAnalyticsReportResponse report,
            Long menuId,
            String menuName
    ) {
        return new AnalyticsDtos.MenuAnalyticsReportResponse(
                menuId,
                menuName,
                report.branchId(),
                report.branchName(),
                report.from(),
                report.to(),
                report.kpis(),
                report.daily(),
                report.hourly(),
                report.devices(),
                report.topProducts(),
                report.topCategories(),
                report.categoryProductTree(),
                report.sampleJourneys(),
                report.funnel(),
                report.feedback()
        );
    }

    private static AnalyticsDtos.MenuRevenueReportResponse stripMenuIdentity(
            AnalyticsDtos.MenuRevenueReportResponse report,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuRevenueReportResponse(
                null,
                null,
                branchId,
                branchName,
                report.from(),
                report.to(),
                report.kpis(),
                report.daily(),
                report.products(),
                report.categories(),
                report.spotlight(),
                report.hourly(),
                report.unsold(),
                report.paymentBreakdown(),
                report.personnel(),
                report.channels(),
                report.channelDaily()
        );
    }

    private static AnalyticsDtos.MerchantStaffPerformanceReportResponse stripMenuIdentity(
            AnalyticsDtos.MerchantStaffPerformanceReportResponse report,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MerchantStaffPerformanceReportResponse(
                null,
                null,
                branchId,
                branchName,
                report.from(),
                report.to(),
                report.kpis(),
                report.waiters(),
                report.daily(),
                report.hourly(),
                report.products()
        );
    }

    @Transactional
    public void applyStatusEvent(SmartReportStatusMessage event) {
        if (event == null || event.jobId() == null) {
            throw new IllegalArgumentException("Smart report status event jobId is required");
        }
        String status = normalizeStatus(event.status());
        if (status == null) {
            throw new IllegalArgumentException("Unsupported smart report status: " + event.status());
        }

        SmartReportEvent reportEvent = smartReportEventRepository.findById(event.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Smart report event not found: " + event.jobId()));

        String current = normalizeStatus(reportEvent.getStatus());
        if (SmartReportEvent.STATUS_COMPLETED.equals(current) || SmartReportEvent.STATUS_FAILED.equals(current)) {
            return;
        }

        if (SmartReportEvent.STATUS_PROCESSING.equals(status)) {
            if (!SmartReportEvent.STATUS_QUEUED.equals(current) && !SmartReportEvent.STATUS_PROCESSING.equals(current)) {
                return;
            }
            reportEvent.setStatus(SmartReportEvent.STATUS_PROCESSING);
            applyTimestamps(reportEvent, event);
            smartReportEventRepository.save(reportEvent);
            return;
        }

        if (SmartReportEvent.STATUS_COMPLETED.equals(status)) {
            String resultText = resolveResultText(event);
            if (resultText == null || resultText.isBlank()) {
                throw new IllegalArgumentException("Smart report completed event requires result payload");
            }
            reportEvent.setStatus(SmartReportEvent.STATUS_COMPLETED);
            reportEvent.setErrorCode(null);
            reportEvent.setErrorMessage(null);
            applyTimestamps(reportEvent, event);
            if (reportEvent.getCompletedAt() == null) {
                reportEvent.setCompletedAt(LocalDateTime.now());
            }
            smartReportEventRepository.save(reportEvent);
            upsertResult(reportEvent, resultText);
            SmartReportDtos.AiSmartReportResult parsed = parseResultText(resultText);
            String title = parsed == null ? null : parsed.title();
            smartReportCompletionNotifier.sendReadyEmail(reportEvent.getProcessId(), title);
            return;
        }

        reportEvent.setStatus(SmartReportEvent.STATUS_FAILED);
        reportEvent.setErrorCode(event.errorCode());
        reportEvent.setErrorMessage(event.errorMessage());
        applyTimestamps(reportEvent, event);
        if (reportEvent.getCompletedAt() == null) {
            reportEvent.setCompletedAt(LocalDateTime.now());
        }
        smartReportEventRepository.save(reportEvent);
        smartReportCompletionNotifier.markFailedNotified(reportEvent.getProcessId());
    }

    @Transactional(readOnly = true)
    public SmartReportDtos.SmartReportQuotaResponse getQuota(Long merchantId) {
        ZoneId zone = zoneId();
        LocalDateTime periodStart = periodStart(zone);
        Instant resetsAt = periodEnd(zone).atZone(zone).toInstant();
        long used = smartReportEventRepository.countByMerchantIdAndCreatedAtGreaterThanEqual(merchantId, periodStart);
        int freeLimit = freeLimit(merchantId);
        long freeRemaining = Math.max(freeLimit - used, 0);
        LocalDate bonusWeek = currentBonusWeek();
        boolean bonusEarned = smartReportFeedbackRepository.existsByMerchantIdAndBonusWeek(merchantId, bonusWeek);
        int bonusCredits = bonusEarned
                && smartReportFeedbackRepository.existsByMerchantIdAndBonusWeekAndBonusUsedAtIsNull(merchantId, bonusWeek)
                ? 1 : 0;
        // The feedback bonus counts as one more free report this week.
        int limit = freeLimit + (bonusEarned ? 1 : 0);
        long remaining = freeRemaining + bonusCredits;
        int paidCredits = paidCredits(merchantId);
        BigDecimal addonUnitPrice = productRepository.findByCode(CatalogProducts.SMART_REPORTING_ADDON)
                .map(com.ael.algoryqrservice.qrservice.model.Product::getUnitPrice)
                .orElse(new BigDecimal("200.00"));
        return new SmartReportDtos.SmartReportQuotaResponse(
                quotaProperties.getQuotaPeriod().name(),
                limit,
                used,
                remaining,
                resetsAt,
                resolveLastUsage(merchantId),
                paidCredits,
                CatalogProducts.SMART_REPORTING_ADDON,
                addonUnitPrice,
                bonusCredits,
                !bonusEarned
        );
    }

    @Transactional(readOnly = true)
    public Page<SmartReportDtos.SmartReportListItem> listJobs(Long merchantId, String status, Pageable pageable) {
        return listJobs(merchantId, status, null, null, pageable);
    }

    /**
     * @param createdFrom first creation day to include (inclusive), or {@code null} for no lower bound
     * @param createdTo   last creation day to include (inclusive), or {@code null} for no upper bound
     */
    @Transactional(readOnly = true)
    public Page<SmartReportDtos.SmartReportListItem> listJobs(
            Long merchantId,
            String status,
            LocalDate createdFrom,
            LocalDate createdTo,
            Pageable pageable
    ) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BadRequestException("Baslangic tarihi bitis tarihinden sonra olamaz");
        }
        String normalized = status != null && "all".equalsIgnoreCase(status.trim())
                ? null
                : normalizeStatus(status);
        Page<SmartReportEvent> page;
        if (createdFrom == null && createdTo == null) {
            page = normalized == null
                    ? smartReportEventRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                    : smartReportEventRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, normalized, pageable);
        } else {
            LocalDateTime from = createdFrom == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : createdFrom.atStartOfDay();
            LocalDateTime before = createdTo == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : createdTo.plusDays(1).atStartOfDay();
            page = normalized == null
                    ? smartReportEventRepository
                            .findByMerchantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                    merchantId, from, before, pageable)
                    : smartReportEventRepository
                            .findByMerchantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                    merchantId, normalized, from, before, pageable);
        }
        return page.map(SmartReportService::toListItem);
    }

    private static SmartReportDtos.SmartReportListItem toListItem(SmartReportEvent event) {
        return new SmartReportDtos.SmartReportListItem(
                event.getProcessId(),
                event.getMenuId(),
                event.getMenuName(),
                event.getBranchId(),
                event.getBranchName(),
                event.getFromDate(),
                event.getToDate(),
                event.getLocale(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getCreditSource()
        );
    }

    /** Reports created over the last {@code days} days (today included), per day and per credit source. */
    @Transactional(readOnly = true)
    public SmartReportDtos.SmartReportUsageResponse getUsage(Long merchantId, int days) {
        if (days != 7 && days != 30) {
            throw new BadRequestException("days 7 veya 30 olmali");
        }
        LocalDate to = LocalDate.now(zoneId());
        LocalDate from = to.minusDays(days - 1L);
        List<SmartReportEvent> events = smartReportEventRepository
                .findByMerchantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(merchantId, from.atStartOfDay());

        Map<LocalDate, long[]> perDay = new java.util.LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            perDay.put(day, new long[3]);
        }
        long[] totals = new long[3];
        for (SmartReportEvent event : events) {
            int slot = creditSlot(event.getCreditSource());
            totals[slot]++;
            long[] bucket = perDay.get(event.getCreatedAt().toLocalDate());
            if (bucket != null) {
                bucket[slot]++;
            }
        }
        List<SmartReportDtos.UsageDay> daily = perDay.entrySet().stream()
                .map(entry -> new SmartReportDtos.UsageDay(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]))
                .toList();
        return new SmartReportDtos.SmartReportUsageResponse(
                days,
                from,
                to,
                new SmartReportDtos.UsageTotals(totals[0], totals[1], totals[2], events.size()),
                daily,
                events.stream().map(SmartReportService::toListItem).toList()
        );
    }

    /** 0 = free, 1 = bonus, 2 = paid; reports without a recorded source count as free. */
    private static int creditSlot(String creditSource) {
        if (SmartReportEvent.CREDIT_BONUS.equals(creditSource)) {
            return 1;
        }
        if (SmartReportEvent.CREDIT_PAID.equals(creditSource)) {
            return 2;
        }
        return 0;
    }

    @Transactional(readOnly = true)
    public SmartReportDtos.SmartReportDetailResponse getJobDetail(Long merchantId, UUID jobId) {
        SmartReportEvent event = smartReportEventRepository.findByProcessIdAndMerchantId(jobId, merchantId)
                .orElseThrow(() -> new NotFoundException("Akilli rapor bulunamadi: " + jobId));
        String storedResult = smartReportResultRepository.findByProcessId(event.getProcessId())
                .map(SmartReportResult::getResultText)
                .orElse(null);
        SmartReportDtos.AiSmartReportResult parsed = parseResultText(storedResult);
        String resultText = displayResultText(parsed, storedResult);
        return new SmartReportDtos.SmartReportDetailResponse(
                event.getProcessId(),
                event.getMenuId(),
                event.getMenuName(),
                event.getBranchId(),
                event.getBranchName(),
                event.getFromDate(),
                event.getToDate(),
                event.getLocale(),
                event.getCreatedAt(),
                event.getStatus(),
                parsed,
                resultText,
                event.getErrorCode(),
                event.getErrorMessage(),
                toInstant(event.getCreatedAt()),
                toInstant(event.getUpdatedAt()),
                toInstant(event.getCompletedAt())
        );
    }

    private static String displayResultText(SmartReportDtos.AiSmartReportResult parsed, String stored) {
        if (parsed != null && parsed.rawMarkdown() != null && !parsed.rawMarkdown().isBlank()) {
            return parsed.rawMarkdown().trim();
        }
        return stored;
    }

    private void upsertResult(SmartReportEvent event, String resultText) {
        SmartReportResult existing = smartReportResultRepository.findByProcessId(event.getProcessId()).orElse(null);
        Long menuId = event.getMenuId() != null ? event.getMenuId() : 0L;
        if (existing == null) {
            smartReportResultRepository.save(SmartReportResult.builder()
                    .menuId(menuId)
                    .processId(event.getProcessId())
                    .resultText(resultText)
                    .build());
            return;
        }
        existing.setMenuId(menuId);
        existing.setResultText(resultText);
        smartReportResultRepository.save(existing);
    }

    private Instant resolveLastUsage(Long merchantId) {
        return smartReportEventRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, Pageable.ofSize(1))
                .getContent()
                .stream()
                .findFirst()
                .map(SmartReportEvent::getCreatedAt)
                .map(this::toInstant)
                .orElse(null);
    }

    private Set<String> smartReportingProductCodes() {
        return productRepository.findByFeatureCode(CatalogProducts.SMART_REPORTING).stream()
                .map(com.ael.algoryqrservice.qrservice.model.Product::getCode)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String resolveResultText(SmartReportStatusMessage event) {
        if (event.result() != null) {
            SmartReportDtos.AiSmartReportResult result = event.result();
            String markdown = result.rawMarkdown();
            if ((markdown == null || markdown.isBlank())
                    && event.resultText() != null
                    && !event.resultText().isBlank()) {
                result = new SmartReportDtos.AiSmartReportResult(
                        result.title(),
                        result.summary(),
                        result.sections(),
                        event.resultText().trim(),
                        result.model(),
                        result.promptVersion(),
                        result.usage()
                );
            }
            try {
                return objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Smart report result serialization failed", ex);
            }
        }
        if (event.resultText() != null && !event.resultText().isBlank()) {
            return event.resultText().trim();
        }
        return null;
    }

    private SmartReportDtos.AiSmartReportResult parseResultText(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return null;
        }
        String trimmed = resultText.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, SmartReportDtos.AiSmartReportResult.class);
            } catch (JsonProcessingException ignored) {
            }
        }
        String summary = trimmed.length() > 280 ? trimmed.substring(0, 280) : trimmed;
        return new SmartReportDtos.AiSmartReportResult(
                "Akilli Rapor",
                summary,
                List.of(),
                trimmed,
                null,
                null,
                null
        );
    }

    private int paidCredits(Long merchantId) {
        return Math.max(fulfillmentClient.openRemaining(merchantId, CatalogProducts.SMART_REPORTING, "ADDON_PURCHASE"), 0);
    }

    /**
     * Free reports per period come from the merchant's package (a {@code SMART_REPORT_QUOTA} line, so
     * each package can grant a different number). Packages without that line keep the configured
     * default ({@code app.smart-report.quota-limit}).
     */
    int freeLimit(Long merchantId) {
        List<com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse> quota;
        try {
            quota = fulfillmentClient.listEntitlements(merchantId).stream()
                    .filter(item -> CatalogProducts.SMART_REPORT_QUOTA.equals(item.featureCode()))
                    .filter(item -> item.status() == null || "ACTIVE".equals(item.status()))
                    .filter(item -> item.expiresAt() == null || item.expiresAt().isAfter(Instant.now()))
                    .toList();
        } catch (RuntimeException exception) {
            quota = List.of();
        }
        if (quota.isEmpty()) {
            return Math.max(quotaProperties.getQuotaLimit(), 0);
        }
        if (quota.stream().anyMatch(item -> item.unlimited())) {
            return Integer.MAX_VALUE;
        }
        return quota.stream().mapToInt(item -> item.quantity() == null ? 0 : item.quantity()).sum();
    }

    /** Monday (in the quota zone) of the current week: the window in which feedback can earn one extra credit. */
    public LocalDate currentBonusWeek() {
        return LocalDate.now(zoneId()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Spends credits in order: the period's free quota, this week's feedback bonus, then purchased credits.
     * Returns the {@code SmartReportEvent.CREDIT_*} source that paid for the report.
     */
    private String assertQuotaAvailable(Long merchantId) {
        ZoneId zone = zoneId();
        long used = smartReportEventRepository.countByMerchantIdAndCreatedAtGreaterThanEqual(merchantId, periodStart(zone));
        if (used < freeLimit(merchantId)) {
            return SmartReportEvent.CREDIT_FREE;
        }
        if (smartReportFeedbackRepository.consumeBonus(merchantId, currentBonusWeek(), LocalDateTime.now(zone)) > 0) {
            return SmartReportEvent.CREDIT_BONUS;
        }
        if (paidCredits(merchantId) > 0) {
            productUsageGateway.use(merchantId, CatalogProducts.SMART_REPORTING_ADDON, 1);
            return SmartReportEvent.CREDIT_PAID;
        }
        String message = quotaProperties.getQuotaPeriod() == SmartReportQuotaProperties.QuotaPeriod.WEEK
                ? "Bu haftaki ucretsiz akilli rapor hakkiniz kullanildi. Ek rapor icin satin alin."
                : "Bugunku ucretsiz akilli rapor hakkiniz kullanildi. Ek rapor icin satin alin.";
        throw new TooManyRequestsException(message);
    }

    private ZoneId zoneId() {
        try {
            return ZoneId.of(quotaProperties.getZone());
        } catch (Exception ignored) {
            return ZoneId.of("Europe/Istanbul");
        }
    }

    private LocalDateTime periodStart(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (quotaProperties.getQuotaPeriod() == SmartReportQuotaProperties.QuotaPeriod.WEEK) {
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return weekStart.atStartOfDay();
        }
        return today.atStartOfDay();
    }

    private LocalDateTime periodEnd(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (quotaProperties.getQuotaPeriod() == SmartReportQuotaProperties.QuotaPeriod.WEEK) {
            LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1);
            return weekEnd.atStartOfDay();
        }
        return today.plusDays(1).atStartOfDay();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SmartReportEvent.STATUS_QUEUED,
                 SmartReportEvent.STATUS_PROCESSING,
                 SmartReportEvent.STATUS_COMPLETED,
                 SmartReportEvent.STATUS_FAILED -> normalized;
            case "running" -> SmartReportEvent.STATUS_PROCESSING;
            default -> null;
        };
    }

    private static void applyTimestamps(SmartReportEvent event, SmartReportStatusMessage statusMessage) {
        if (statusMessage.updatedAt() != null) {
            event.setUpdatedAt(LocalDateTime.ofInstant(statusMessage.updatedAt(), ZoneId.systemDefault()));
        }
        if (statusMessage.completedAt() != null) {
            event.setCompletedAt(LocalDateTime.ofInstant(statusMessage.completedAt(), ZoneId.systemDefault()));
        }
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
