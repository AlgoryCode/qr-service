package com.ael.algoryqrservice.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class SiteAnalyticsDtos {

    private SiteAnalyticsDtos() {
    }

    public record RecordVisitRequest(
            String path,
            String referrer
    ) {
    }

    public record VisitItemResponse(
            Long id,
            String path,
            String referrer,
            String ipAddress,
            String userAgent,
            String device,
            String deviceType,
            String countryCode,
            String countryName,
            String regionName,
            String city,
            Double latitude,
            Double longitude,
            LocalDateTime createdAt
    ) {
    }

    public record VisitPageResponse(
            List<VisitItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record NamedCount(
            String name,
            long count
    ) {
    }

    public record DailyCount(
            String date,
            long count
    ) {
    }

    public record SummaryResponse(
            long totalVisits,
            long uniqueCountries,
            List<NamedCount> devices,
            List<NamedCount> countries,
            List<DailyCount> daily
    ) {
    }
}
