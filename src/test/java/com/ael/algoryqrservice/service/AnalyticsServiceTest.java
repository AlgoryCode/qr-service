package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductVisitRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private MenuVisitRepository menuVisitRepository;
    @Mock
    private MenuProductVisitRepository menuProductVisitRepository;
    @Mock
    private MenuAnalyticsSessionRepository sessionRepository;
    @Mock
    private MenuAnalyticsEventRepository eventRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuCategoryRepository menuCategoryRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(
                menuVisitRepository,
                menuProductVisitRepository,
                sessionRepository,
                eventRepository,
                menuRepository,
                menuProductRepository,
                menuCategoryRepository
        );
    }

    @Test
    void recordEvents_whenValidBatch_thenUpsertsSessionAndSavesEvents() {
        UUID sessionId = UUID.randomUUID();
        Menu menu = publicMenu(5L, 9L);
        when(menuRepository.findById(5L)).thenReturn(Optional.of(menu));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(eventRepository.countBySessionIdAndMenuId(sessionId, 5L)).thenReturn(0L);
        when(menuCategoryRepository.findByCategoryIdAndDeletedFalse(3L))
                .thenReturn(Optional.of(MenuCategory.builder().categoryId(3L).menuId(5L).name("Icecek").build()));
        when(menuProductRepository.findByProductIdAndDeletedFalse(11L))
                .thenReturn(Optional.of(MenuProduct.builder().productId(11L).menuId(5L).name("Cay").build()));

        AnalyticsDtos.AnalyticsEventsRequest request = new AnalyticsDtos.AnalyticsEventsRequest(
                sessionId,
                "MOBILE",
                List.of(
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.MENU_OPEN, null, null, 1, null),
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.CATEGORY_VIEW, 3L, null, 2, null),
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.PRODUCT_VIEW, 3L, 11L, 3, null)
                )
        );

        service.recordEvents(5L, request, "1.2.3.4", "Mozilla/5.0 (iPhone)");

        ArgumentCaptor<MenuAnalyticsSession> sessionCaptor = ArgumentCaptor.forClass(MenuAnalyticsSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getId()).isEqualTo(sessionId);
        assertThat(sessionCaptor.getValue().getMenuId()).isEqualTo(5L);
        assertThat(sessionCaptor.getValue().getDeviceType()).isEqualTo("MOBILE");
        assertThat(sessionCaptor.getValue().getIpHash()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuAnalyticsEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(3);
        assertThat(eventsCaptor.getValue().get(0).getEventType()).isEqualTo(MenuAnalyticsEventType.MENU_OPEN);
        assertThat(eventsCaptor.getValue().get(2).getProductId()).isEqualTo(11L);
    }

    @Test
    void recordEvents_whenTooManyEvents_thenBadRequest() {
        UUID sessionId = UUID.randomUUID();
        when(menuRepository.findById(5L)).thenReturn(Optional.of(publicMenu(5L, 9L)));
        List<AnalyticsDtos.AnalyticsEventItemRequest> events = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> new AnalyticsDtos.AnalyticsEventItemRequest(
                        MenuAnalyticsEventType.MENU_OPEN, null, null, i + 1, null))
                .toList();

        assertThatThrownBy(() -> service.recordEvents(
                5L,
                new AnalyticsDtos.AnalyticsEventsRequest(sessionId, null, events),
                "127.0.0.1",
                "ua"
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void recordEvents_whenDeviceTypeInBody_thenUsesClientDeviceType() {
        UUID sessionId = UUID.randomUUID();
        when(menuRepository.findById(5L)).thenReturn(Optional.of(publicMenu(5L, 9L)));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(eventRepository.countBySessionIdAndMenuId(sessionId, 5L)).thenReturn(0L);

        service.recordEvents(
                5L,
                new AnalyticsDtos.AnalyticsEventsRequest(
                        sessionId,
                        "TABLET",
                        List.of(new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.MENU_OPEN, null, null, 1, null))
                ),
                "127.0.0.1",
                "axios/1.6.0"
        );

        ArgumentCaptor<MenuAnalyticsSession> sessionCaptor = ArgumentCaptor.forClass(MenuAnalyticsSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getDeviceType()).isEqualTo("TABLET");
    }

    @Test
    void recordEvents_whenPublicAccessDisabled_thenForbidden() {
        Menu menu = publicMenu(5L, 9L);
        menu.setPublicAccessEnabled(false);
        when(menuRepository.findById(5L)).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> service.recordEvents(
                5L,
                new AnalyticsDtos.AnalyticsEventsRequest(
                        UUID.randomUUID(),
                        "MOBILE",
                        List.of(new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.MENU_OPEN, null, null, 1, null))
                ),
                "127.0.0.1",
                "ua"
        )).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void getMenuReport_whenOwner_thenAggregatesKpisAndDaily() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 2);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(publicMenu(menuId, ownerId)));
        when(sessionRepository.countByMenuIdAndPeriod(eq(menuId), any(), any())).thenReturn(4L);
        when(eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                eq(menuId), eq(MenuAnalyticsEventType.MENU_OPEN), any(), any())).thenReturn(5L);
        when(eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                eq(menuId), eq(MenuAnalyticsEventType.PRODUCT_VIEW), any(), any())).thenReturn(8L);
        when(eventRepository.countByMenuIdAndEventTypeAndOccurredAtBetween(
                eq(menuId), eq(MenuAnalyticsEventType.CATEGORY_VIEW), any(), any())).thenReturn(6L);
        when(eventRepository.avgProductsPerSession(eq(menuId), any(), any())).thenReturn(2.0);
        when(sessionRepository.countDailyByMenuIdAndPeriod(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf(from), 2L}));
        when(eventRepository.countDailyOpenAndProductByMenuId(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf(from), 3L, 4L}));
        when(eventRepository.countHourlyByMenuId(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{12, 7L}));
        when(sessionRepository.countByDeviceTypeAndPeriod(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"MOBILE", 3L}, new Object[]{"DESKTOP", 1L}));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId))
                .thenReturn(List.of(MenuProduct.builder().productId(11L).menuId(menuId).name("Cay").build()));
        when(menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(menuId))
                .thenReturn(List.of(MenuCategory.builder().categoryId(3L).menuId(menuId).name("Icecek").build()));
        when(eventRepository.topProducts(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 8L}));
        when(eventRepository.topCategories(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 6L}));
        when(eventRepository.productViewsByCategory(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 11L, 8L}));
        when(sessionRepository.findRecentByMenuIdAndPeriod(eq(menuId), any(), any()))
                .thenReturn(List.of());

        AnalyticsDtos.MenuAnalyticsReportResponse report = service.getMenuReport(menuId, ownerId, from, to);

        assertThat(report.menuId()).isEqualTo(menuId);
        assertThat(report.kpis().sessions()).isEqualTo(4L);
        assertThat(report.kpis().menuOpens()).isEqualTo(5L);
        assertThat(report.kpis().productViews()).isEqualTo(8L);
        assertThat(report.kpis().avgProductsPerSession()).isEqualTo(2.0);
        assertThat(report.daily()).hasSize(2);
        assertThat(report.daily().getFirst().sessions()).isEqualTo(2L);
        assertThat(report.hourly()).hasSize(24);
        assertThat(report.hourly().get(12).views()).isEqualTo(7L);
        assertThat(report.topProducts().getFirst().name()).isEqualTo("Cay");
        assertThat(report.topCategories().getFirst().name()).isEqualTo("Icecek");
        assertThat(report.funnel().menuOpens()).isEqualTo(5L);
        assertThat(report.categoryProductTree()).isNotEmpty();
    }

    @Test
    void getMenuReport_whenNotOwner_thenForbidden() {
        when(menuRepository.findById(5L)).thenReturn(Optional.of(publicMenu(5L, 9L)));

        assertThatThrownBy(() -> service.getMenuReport(5L, 99L, LocalDate.now().minusDays(7), LocalDate.now()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(403);
    }

    private Menu publicMenu(Long menuId, Long ownerId) {
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setUserId(ownerId);
        menu.setBusinessName("Cafe");
        menu.setActive(true);
        menu.setDeleted(false);
        menu.setPublicAccessEnabled(true);
        menu.setThemeId("classic");
        menu.setQrId(100L);
        return menu;
    }
}
