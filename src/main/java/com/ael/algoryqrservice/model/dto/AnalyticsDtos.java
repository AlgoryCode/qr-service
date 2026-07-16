package com.ael.algoryqrservice.model.dto;

import java.time.LocalDate;
import java.util.List;

public class AnalyticsDtos {

    public record VisitSummaryResponse(
            long totalVisits,
            long uniqueIpCount,
            long mobileCount,
            long tabletCount,
            long desktopCount
    ) {}

    public record DailyVisitResponse(
            LocalDate date,
            long count
    ) {}

    public record VisitPageResponse(
            VisitSummaryResponse summary,
            List<DailyVisitResponse> daily
    ) {}
}
