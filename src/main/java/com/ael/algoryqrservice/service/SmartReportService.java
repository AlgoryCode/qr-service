package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.config.SmartReportQuotaProperties;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.config.SmartReportRabbitProperties;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.exception.TooManyRequestsException;
import com.ael.algoryqrservice.messaging.dto.SmartReportGenerateMessage;
import com.ael.algoryqrservice.messaging.dto.SmartReportStatusMessage;
import com.ael.algoryqrservice.model.SmartReportEvent;
import com.ael.algoryqrservice.model.SmartReportResult;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.ael.algoryqrservice.repository.SmartReportEventRepository;
import com.ael.algoryqrservice.repository.SmartReportResultRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartReportService {

    private final AnalyticsService analyticsService;
    private final RabbitTemplate rabbitTemplate;
    private final SmartReportRabbitProperties smartReportRabbitProperties;
    private final SmartReportQuotaProperties quotaProperties;
    private final SmartReportEventRepository smartReportEventRepository;
    private final SmartReportResultRepository smartReportResultRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final ProductRepository productRepository;
    private final SmartReportCompletionNotifier smartReportCompletionNotifier;
    private final ObjectMapper objectMapper;

    @Transactional
    public SmartReportDtos.SmartReportAccepted enqueue(
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        return enqueue(null, menuId, ownerId, from, to, locale, options);
    }

    @Transactional
    public SmartReportDtos.SmartReportAccepted enqueueForBranch(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        return enqueue(branchId, menuId, ownerId, from, to, locale, options);
    }

    private SmartReportDtos.SmartReportAccepted enqueue(
            Long branchId,
            Long menuId,
            Long ownerId,
            LocalDate from,
            LocalDate to,
            String locale,
            SmartReportDtos.Options options
    ) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }

        assertQuotaAvailable(ownerId);

        AnalyticsDtos.MenuAnalyticsReportResponse report = branchId != null
                ? analyticsService.getBranchReport(branchId, menuId, ownerId, from, to)
                : analyticsService.getMenuReport(menuId, ownerId, from, to);

        UUID processId = UUID.randomUUID();
        String resolvedLocale = locale == null || locale.isBlank() ? "tr" : locale.trim();
        Long resolvedMenuId = report.menuId();
        String menuName = report.menuName();
        Long resolvedBranchId = report.branchId() != null ? report.branchId() : branchId;
        String branchName = report.branchName();
        if (menuName == null || menuName.isBlank()) {
            menuName = branchName != null && !branchName.isBlank()
                    ? branchName
                    : (resolvedMenuId != null ? "Menu #" + resolvedMenuId : "Sube");
        }
        Long payloadMenuId = resolvedMenuId != null ? resolvedMenuId : 0L;
        AnalyticsDtos.MenuAnalyticsReportResponse aiReport = new AnalyticsDtos.MenuAnalyticsReportResponse(
                payloadMenuId,
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

        smartReportEventRepository.save(SmartReportEvent.builder()
                .processId(processId)
                .userId(ownerId)
                .menuId(resolvedMenuId)
                .menuName(resolvedMenuId != null ? menuName : null)
                .branchId(resolvedBranchId)
                .branchName(branchName)
                .fromDate(from)
                .toDate(to)
                .locale(resolvedLocale)
                .status(SmartReportEvent.STATUS_QUEUED)
                .build());

        touchSmartReportLastUsage(ownerId);

        SmartReportGenerateMessage payload = new SmartReportGenerateMessage(
                processId,
                ownerId,
                payloadMenuId,
                aiReport,
                resolvedLocale,
                SmartReportDtos.toOptionsMap(options)
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
                "Smart report queued. processId={} branchId={} menuId={} userId={} from={} to={}",
                processId,
                resolvedBranchId,
                resolvedMenuId,
                ownerId,
                from,
                to
        );
        return new SmartReportDtos.SmartReportAccepted(processId, SmartReportEvent.STATUS_QUEUED);
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
            touchSmartReportLastUsage(reportEvent.getUserId());
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
        String resultText = smartReportResultRepository.findByProcessId(event.getProcessId())
                .map(SmartReportResult::getResultText)
                .orElse(null);
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
                parseResultText(resultText),
                resultText,
                event.getErrorCode(),
                event.getErrorMessage(),
                toInstant(event.getCreatedAt()),
                toInstant(event.getUpdatedAt()),
                toInstant(event.getCompletedAt())
        );
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

    private void touchSmartReportLastUsage(Long userId) {
        if (userId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> smartReportingCodes = smartReportingProductCodes();
        List<UserEntitlement> entitlements = userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (UserEntitlement entitlement : entitlements) {
            if (smartReportingCodes.contains(entitlement.getProductCode())) {
                entitlement.setLastUsage(now);
                userEntitlementRepository.save(entitlement);
            }
        }
    }

    private Instant resolveLastUsage(Long userId) {
        Set<String> smartReportingCodes = smartReportingProductCodes();
        return userEntitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(entitlement -> smartReportingCodes.contains(entitlement.getProductCode()))
                .map(UserEntitlement::getLastUsage)
                .filter(value -> value != null)
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
        if (event.resultText() != null && !event.resultText().isBlank()) {
            return event.resultText().trim();
        }
        if (event.result() == null) {
            return null;
        }
        if (event.result().rawMarkdown() != null && !event.result().rawMarkdown().isBlank()) {
            try {
                return objectMapper.writeValueAsString(event.result());
            } catch (JsonProcessingException ex) {
                return event.result().rawMarkdown().trim();
            }
        }
        try {
            return objectMapper.writeValueAsString(event.result());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Smart report result serialization failed", ex);
        }
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
