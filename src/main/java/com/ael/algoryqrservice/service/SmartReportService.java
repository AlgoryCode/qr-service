package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.config.SmartReportRabbitProperties;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.messaging.dto.SmartReportGenerateMessage;
import com.ael.algoryqrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.SmartReportResult;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.ael.algoryqrservice.model.dto.SmartReportModelDtos;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.repository.SmartReportResultRepository;
import com.ael.algoryqrservice.util.AppTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartReportService {

    private final AnalyticsService analyticsService;
    private final SmartReportModelInputBuilder smartReportModelInputBuilder;
    private final RabbitTemplate rabbitTemplate;
    private final SmartReportRabbitProperties smartReportRabbitProperties;
    private final SmartReportQuotaProperties quotaProperties;
    private final SmartReportEventRepository smartReportEventRepository;
    private final SmartReportResultRepository smartReportResultRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final FulfillmentUsageLogRepository fulfillmentUsageLogRepository;
    private final FulfillmentGateService fulfillmentGateService;
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
        return enqueue(branchId, null, ownerId, from, to, locale, options, true);
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
        return enqueue(null, menuId, ownerId, from, to, locale, options, false);
    }

    private SmartReportDtos.SmartReportAccepted enqueue(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options,
            boolean branchOnly
    ) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        if (branchOnly && branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId is required");
        }
        if (!branchOnly && menuId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "menuId is required");
        }

        assertQuotaAvailable(ownerId);

        Long scopedMenuId = branchOnly ? null : menuId;
        AnalyticsDtos.MenuAnalyticsReportResponse report = branchOnly
                ? analyticsService.getBranchReport(branchId, null, ownerId, from, to)
                : analyticsService.getMenuReport(menuId, ownerId, from, to);

        AnalyticsDtos.MenuRevenueReportResponse revenue = branchOnly
                ? analyticsService.getBranchRevenueReport(branchId, null, ownerId, from, to)
                : analyticsService.getMenuRevenueReport(menuId, ownerId, from, to);

        AnalyticsDtos.MenuWaiterPerformanceReportResponse waiter = branchOnly
                ? analyticsService.getBranchWaiterPerformanceReport(branchId, null, ownerId, from, to)
                : analyticsService.getMenuWaiterPerformanceReport(menuId, ownerId, from, to);

        UUID processId = UUID.randomUUID();
        String resolvedLocale = locale == null || locale.isBlank() ? "tr" : locale.trim();
        Long resolvedBranchId = report.branchId() != null ? report.branchId() : branchId;
        String branchName = report.branchName();
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
        AnalyticsDtos.MenuWaiterPerformanceReportResponse aiWaiter = branchOnly
                ? stripMenuIdentity(waiter, resolvedBranchId, branchName)
                : waiter;

        smartReportEventRepository.save(SmartReportEvent.builder()
                .processId(processId)
                .userId(ownerId)
                .menuId(resolvedMenuId)
                .menuName(menuName)
                .branchId(resolvedBranchId)
                .branchName(branchName)
                .fromDate(from)
                .toDate(to)
                .locale(resolvedLocale)
                .status(SmartReportEvent.STATUS_QUEUED)
                .build());

        touchSmartReportLastUsage(ownerId, scopedMenuId != null ? scopedMenuId : resolvedBranchId);

        Map<String, Object> optionsMap = SmartReportDtos.toOptionsMap(options);
        if (branchOnly) {
            Map<String, Object> branchOptions = optionsMap == null ? new HashMap<>() : new HashMap<>(optionsMap);
            branchOptions.putIfAbsent(
                    "focusAreas",
                    List.of("şube siparişleri", "kanal kıyası", "ciro ve ürün satışları")
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

        SmartReportGenerateMessage payload = new SmartReportGenerateMessage(
                processId,
                ownerId,
                resolvedMenuId,
                resolvedBranchId,
                input,
                resolvedLocale
        );

        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding("UTF-8");
            properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
            Message message = new Message(body, properties);
            rabbitTemplate.send(smartReportRabbitProperties.getQueue(), message);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Smart report message serialization failed",
                    ex
            );
        }

        log.info(
                "Smart report queued. processId={} branchId={} menuId={} branchOnly={} userId={} from={} to={}",
                processId,
                resolvedBranchId,
                resolvedMenuId,
                branchOnly,
                ownerId,
                from,
                to
        );
        return new SmartReportDtos.SmartReportAccepted(processId, SmartReportEvent.STATUS_QUEUED);
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

    private static AnalyticsDtos.MenuWaiterPerformanceReportResponse stripMenuIdentity(
            AnalyticsDtos.MenuWaiterPerformanceReportResponse report,
            Long branchId,
            String branchName
    ) {
        return new AnalyticsDtos.MenuWaiterPerformanceReportResponse(
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
            touchSmartReportLastUsage(reportEvent.getUserId(), reportEvent.getMenuId());
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
    public SmartReportDtos.SmartReportQuotaResponse getQuota(Long userId) {
        ZoneId zone = zoneId();
        LocalDateTime periodStart = periodStart(zone);
        Instant resetsAt = periodEnd(zone).atZone(zone).toInstant();
        long used = smartReportEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, periodStart);
        int limit = Math.max(quotaProperties.getQuotaLimit(), 0);
        long remaining = Math.max(limit - used, 0);
        return new SmartReportDtos.SmartReportQuotaResponse(
                quotaProperties.getQuotaPeriod().name(),
                limit,
                used,
                remaining,
                resetsAt,
                resolveLastUsage(userId)
        );
    }

    @Transactional(readOnly = true)
    public Page<SmartReportDtos.SmartReportListItem> listJobs(Long userId, String status, Pageable pageable) {
        String normalized = status != null && "all".equalsIgnoreCase(status.trim())
                ? null
                : normalizeStatus(status);
        Page<SmartReportEvent> page = normalized == null
                ? smartReportEventRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : smartReportEventRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, normalized, pageable);
        return page.map(event -> new SmartReportDtos.SmartReportListItem(
                event.getProcessId(),
                event.getMenuId(),
                event.getMenuName(),
                event.getBranchId(),
                event.getBranchName(),
                event.getFromDate(),
                event.getToDate(),
                event.getLocale(),
                event.getStatus(),
                event.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public SmartReportDtos.SmartReportDetailResponse getJobDetail(Long userId, UUID jobId) {
        SmartReportEvent event = smartReportEventRepository.findByProcessIdAndUserId(jobId, userId)
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
        if (existing == null) {
            smartReportResultRepository.save(SmartReportResult.builder()
                    .menuId(event.getMenuId())
                    .processId(event.getProcessId())
                    .resultText(resultText)
                    .build());
            return;
        }
        existing.setMenuId(event.getMenuId());
        existing.setResultText(resultText);
        smartReportResultRepository.save(existing);
    }

    private void touchSmartReportLastUsage(Long userId, Long menuId) {
        if (userId == null) {
            return;
        }
        fulfillmentGateService.logFeatureUsage(
                userId,
                CatalogProducts.SMART_REPORTING,
                FulfillmentReferenceType.FEATURE,
                menuId
        );
    }

    private Instant resolveLastUsage(Long userId) {
        Set<Long> detailIds = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal()).stream()
                .filter(detail -> CatalogProducts.SMART_REPORTING.equals(detail.getFeatureCode())
                        || (detail.getScopeCode() != null && detail.getScopeCode().contains("SMART_REPORTING")))
                .map(FulfillmentDetail::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (detailIds.isEmpty()) {
            return null;
        }
        return fulfillmentUsageLogRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.ofSize(100))
                .getContent()
                .stream()
                .filter(log -> detailIds.contains(log.getDetailId()))
                .map(FulfillmentUsageLog::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(this::toInstant)
                .orElse(null);
    }

    private Set<String> smartReportingProductCodes() {
        return productRepository.findByFeatureCode(CatalogProducts.SMART_REPORTING).stream()
                .map(com.ael.algoryqrservice.model.Product::getCode)
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

    private void assertQuotaAvailable(Long userId) {
        SmartReportDtos.SmartReportQuotaResponse quota = getQuota(userId);
        if (quota.remaining() <= 0) {
            String message = quotaProperties.getQuotaPeriod() == SmartReportQuotaProperties.QuotaPeriod.WEEK
                    ? "Bu haftaki akilli rapor hakkiniz kullanildi"
                    : "Bugunku akilli rapor hakkiniz kullanildi";
            throw new TooManyRequestsException(message);
        }
        if (quota.limit() == 1 && isLastUsageWithinPeriod(quota.lastUsage())) {
            String message = quotaProperties.getQuotaPeriod() == SmartReportQuotaProperties.QuotaPeriod.WEEK
                    ? "Bu haftaki akilli rapor hakkiniz kullanildi"
                    : "Bugunku akilli rapor hakkiniz kullanildi";
            throw new TooManyRequestsException(message);
        }
    }

    private boolean isLastUsageWithinPeriod(Instant lastUsage) {
        if (lastUsage == null) {
            return false;
        }
        ZoneId zone = zoneId();
        LocalDateTime periodStart = periodStart(zone);
        return !lastUsage.isBefore(periodStart.atZone(zone).toInstant());
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
