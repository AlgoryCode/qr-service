package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.SiteVisit;
import com.ael.algoryqrservice.model.dto.SiteAnalyticsDtos;
import com.ael.algoryqrservice.repository.SiteVisitRepository;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.ClientInfo;
import com.ael.algoryqrservice.util.DeviceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SiteAnalyticsService {

    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_REFERRER_LENGTH = 1024;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final SiteVisitRepository siteVisitRepository;
    private final IpGeoLookupService ipGeoLookupService;

    @Transactional
    public void recordVisit(SiteAnalyticsDtos.RecordVisitRequest request, ClientInfo clientInfo) {
        String path = normalizePath(request.path());
        if (path == null) {
            throw new BadRequestException("path zorunludur");
        }

        String userAgent = clientInfo.userAgent() != null ? clientInfo.userAgent() : "unknown";
        Optional<IpGeoLookupService.GeoLocation> geo = ipGeoLookupService.lookup(clientInfo.ipAddress());

        SiteVisit.SiteVisitBuilder builder = SiteVisit.builder()
                .path(path)
                .referrer(truncate(request.referrer(), MAX_REFERRER_LENGTH))
                .ipAddress(truncate(clientInfo.ipAddress(), 45))
                .userAgent(truncate(userAgent, 512))
                .device(DeviceUtils.resolveDeviceName(userAgent))
                .deviceType(DeviceUtils.resolveDeviceType(userAgent))
                .createdAt(AppTime.nowLocal());

        geo.ifPresent(location -> builder
                .countryCode(truncate(location.countryCode(), 8))
                .countryName(truncate(location.countryName(), 120))
                .regionName(truncate(location.regionName(), 120))
                .city(truncate(location.city(), 120))
                .latitude(location.latitude())
                .longitude(location.longitude()));

        siteVisitRepository.save(builder.build());
    }

    @Transactional(readOnly = true)
    public SiteAnalyticsDtos.VisitPageResponse listVisits(int page, int size, Integer days) {
        DateRange range = resolveRange(days);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<SiteVisit> result = siteVisitRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                range.from(),
                range.to(),
                pageable
        );

        return new SiteAnalyticsDtos.VisitPageResponse(
                result.getContent().stream().map(this::toItem).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public SiteAnalyticsDtos.SummaryResponse summary(Integer days) {
        DateRange range = resolveRange(days);
        long totalVisits = siteVisitRepository.countByCreatedAtBetween(range.from(), range.to());

        List<SiteAnalyticsDtos.NamedCount> devices = siteVisitRepository
                .countByDeviceTypeBetween(range.from(), range.to())
                .stream()
                .map(row -> new SiteAnalyticsDtos.NamedCount(
                        deviceLabel(row[0] != null ? row[0].toString() : "UNKNOWN"),
                        ((Number) row[1]).longValue()
                ))
                .toList();

        List<SiteAnalyticsDtos.NamedCount> countries = siteVisitRepository
                .countByCountryBetween(range.from(), range.to())
                .stream()
                .map(row -> new SiteAnalyticsDtos.NamedCount(
                        row[0] != null ? row[0].toString() : "Bilinmiyor",
                        ((Number) row[1]).longValue()
                ))
                .sorted(Comparator.comparingLong(SiteAnalyticsDtos.NamedCount::count).reversed())
                .limit(10)
                .toList();

        List<SiteAnalyticsDtos.DailyCount> daily = siteVisitRepository
                .countByDayBetween(range.from(), range.to())
                .stream()
                .map(row -> new SiteAnalyticsDtos.DailyCount(
                        formatDay(row[0]),
                        ((Number) row[1]).longValue()
                ))
                .toList();

        long uniqueCountries = countries.stream()
                .filter(item -> !"Bilinmiyor".equals(item.name()))
                .count();

        return new SiteAnalyticsDtos.SummaryResponse(
                totalVisits,
                uniqueCountries,
                devices,
                countries,
                daily
        );
    }

    private SiteAnalyticsDtos.VisitItemResponse toItem(SiteVisit visit) {
        return new SiteAnalyticsDtos.VisitItemResponse(
                visit.getId(),
                visit.getPath(),
                visit.getReferrer(),
                visit.getIpAddress(),
                visit.getUserAgent(),
                visit.getDevice(),
                visit.getDeviceType(),
                visit.getCountryCode(),
                visit.getCountryName(),
                visit.getRegionName(),
                visit.getCity(),
                visit.getLatitude(),
                visit.getLongitude(),
                visit.getCreatedAt()
        );
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > MAX_PATH_LENGTH) {
            normalized = normalized.substring(0, MAX_PATH_LENGTH);
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private DateRange resolveRange(Integer days) {
        int safeDays = days == null || days < 1 ? DEFAULT_RANGE_DAYS : Math.min(days, 365);
        LocalDateTime to = AppTime.nowLocal();
        LocalDateTime from = to.minusDays(safeDays);
        return new DateRange(from, to);
    }

    private String deviceLabel(String deviceType) {
        return switch (deviceType == null ? "UNKNOWN" : deviceType.toUpperCase(Locale.ROOT)) {
            case "MOBILE" -> "Mobil";
            case "TABLET" -> "Tablet";
            case "DESKTOP" -> "Masaüstü";
            default -> "Diğer";
        };
    }

    private String formatDay(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return value != null ? value.toString() : "";
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
    }
}
