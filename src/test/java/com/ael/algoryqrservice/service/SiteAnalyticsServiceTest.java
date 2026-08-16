package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.SiteVisit;
import com.ael.algoryqrservice.model.dto.SiteAnalyticsDtos;
import com.ael.algoryqrservice.repository.SiteVisitRepository;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAnalyticsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-15T12:00:00Z");

    @Mock
    private SiteVisitRepository siteVisitRepository;

    @Mock
    private IpGeoLookupService ipGeoLookupService;

    private SiteAnalyticsService service;

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(FIXED_INSTANT, ZONE));
        service = new SiteAnalyticsService(siteVisitRepository, ipGeoLookupService);
    }

    @AfterEach
    void tearDown() {
        AppTime.resetClock();
    }

    @Test
    void recordVisit_persistsClientAndGeoData() {
        when(ipGeoLookupService.lookup("203.0.113.10")).thenReturn(Optional.of(
                new IpGeoLookupService.GeoLocation("TR", "Turkey", "Istanbul", "Istanbul", 41.0, 29.0)
        ));

        ClientInfo clientInfo = new ClientInfo(
                "203.0.113.10",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
                "iPhone",
                "MOBILE"
        );

        service.recordVisit(new SiteAnalyticsDtos.RecordVisitRequest("/contact", "https://google.com"), clientInfo);

        ArgumentCaptor<SiteVisit> captor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(siteVisitRepository).save(captor.capture());

        SiteVisit saved = captor.getValue();
        assertThat(saved.getPath()).isEqualTo("/contact");
        assertThat(saved.getReferrer()).isEqualTo("https://google.com");
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(saved.getDeviceType()).isEqualTo("MOBILE");
        assertThat(saved.getCountryCode()).isEqualTo("TR");
        assertThat(saved.getCity()).isEqualTo("Istanbul");
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.ofInstant(FIXED_INSTANT, ZONE));
    }

    @Test
    void recordVisit_requiresPath() {
        ClientInfo clientInfo = new ClientInfo("8.8.8.8", "Mozilla/5.0", "Desktop", "DESKTOP");

        assertThatThrownBy(() -> service.recordVisit(
                new SiteAnalyticsDtos.RecordVisitRequest("  ", null),
                clientInfo
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void summary_returnsAggregatedCounts() {
        LocalDateTime to = AppTime.nowLocal();
        LocalDateTime from = to.minusDays(30);

        when(siteVisitRepository.countByCreatedAtBetween(from, to)).thenReturn(42L);
        when(siteVisitRepository.countByDeviceTypeBetween(from, to)).thenReturn(List.of(
                new Object[]{"MOBILE", 30L},
                new Object[]{"DESKTOP", 12L}
        ));
        when(siteVisitRepository.countByCountryBetween(from, to)).thenReturn(List.of(
                new Object[]{"Turkey", 25L},
                new Object[]{"Germany", 10L}
        ));
        when(siteVisitRepository.countByDayBetween(from, to)).thenReturn(List.of(
                new Object[]{LocalDate.of(2026, 8, 14), 20L},
                new Object[]{LocalDate.of(2026, 8, 15), 22L}
        ));

        SiteAnalyticsDtos.SummaryResponse summary = service.summary(30);

        assertThat(summary.totalVisits()).isEqualTo(42L);
        assertThat(summary.uniqueCountries()).isEqualTo(2L);
        assertThat(summary.devices()).extracting(SiteAnalyticsDtos.NamedCount::name)
                .containsExactly("Mobil", "Masaüstü");
        assertThat(summary.countries()).hasSize(2);
        assertThat(summary.daily()).hasSize(2);
    }

    @Test
    void listVisits_returnsPagedItems() {
        LocalDateTime to = AppTime.nowLocal();
        LocalDateTime from = to.minusDays(30);

        SiteVisit visit = SiteVisit.builder()
                .id(1L)
                .path("/")
                .ipAddress("1.2.3.4")
                .device("Chrome")
                .deviceType("DESKTOP")
                .createdAt(to)
                .build();

        when(siteVisitRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                eq(from),
                eq(to),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(visit)));

        SiteAnalyticsDtos.VisitPageResponse response = service.listVisits(0, 20, 30);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().path()).isEqualTo("/");
        assertThat(response.totalElements()).isEqualTo(1L);
    }
}
