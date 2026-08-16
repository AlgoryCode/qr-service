package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.SiteAnalyticsDtos;
import com.ael.algoryqrservice.service.SiteAnalyticsService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics/site")
@RequiredArgsConstructor
public class SiteAnalyticsController {

    private final SiteAnalyticsService siteAnalyticsService;

    @PostMapping("/visit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordVisit(
            @Valid @RequestBody SiteAnalyticsDtos.RecordVisitRequest request,
            HttpServletRequest httpRequest
    ) {
        siteAnalyticsService.recordVisit(request, ClientInfo.from(httpRequest));
    }
}
