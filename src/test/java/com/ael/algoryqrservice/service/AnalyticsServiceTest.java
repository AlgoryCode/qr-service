package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductVisitRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuVisitRepository;
import com.ael.algoryqrservice.repository.SubCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private SubCategoryRepository subCategoryRepository;
    @Mock
    private MenuFeedbackService menuFeedbackService;
    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private MenuWaiterRepository menuWaiterRepository;

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
                subCategoryRepository,
                menuFeedbackService,
                menuOrderRepository,
                menuWaiterRepository
        );
    }

    @Test
    void recordEvents_whenValidBatch_thenUpsertsSessionAndSavesEvents() {
        UUID sessionId = UUID.randomUUID();
        Menu menu = publicMenu(5L, 9L);
        when(menuRepository.findById(5L)).thenReturn(Optional.of(menu));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(eventRepository.countBySessionIdAndMenuId(sessionId, 5L)).thenReturn(0L);
        when(subCategoryRepository.findByIdAndDeletedFalse(3L))
                .thenReturn(Optional.of(SubCategory.builder().id(3L).mainCategoryId(1L).slug("taze").name("Icecek").sortOrder(1).build()));
        when(menuProductRepository.findByProductIdAndDeletedFalse(11L))
                .thenReturn(Optional.of(cayProduct(5L)));

        AnalyticsDtos.AnalyticsEventsRequest request = new AnalyticsDtos.AnalyticsEventsRequest(
                sessionId,
                "MOBILE",
                List.of(
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.MENU_OPEN, null, null, null, 1, null),
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.CATEGORY_VIEW, 3L, null, null, 2, null),
                        new AnalyticsDtos.AnalyticsEventItemRequest(
                                MenuAnalyticsEventType.PRODUCT_VIEW, 3L, 11L, null, 3, null)
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
                        MenuAnalyticsEventType.MENU_OPEN, null, null, null, i + 1, null))
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
                                MenuAnalyticsEventType.MENU_OPEN, null, null, null, 1, null))
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
                                MenuAnalyticsEventType.MENU_OPEN, null, null, null, 1, null))
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
                .thenReturn(List.of(cayProduct(menuId)));
        when(subCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(SubCategory.builder().id(3L).mainCategoryId(1L).slug("icecek").name("Icecek").sortOrder(1).build()));
        when(eventRepository.topProducts(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 8L}));
        when(eventRepository.topCategories(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 6L}));
        when(eventRepository.productViewsByCategory(eq(menuId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 11L, 8L}));
        when(sessionRepository.findRecentByMenuIdAndPeriod(eq(menuId), any(), any()))
                .thenReturn(List.of());
        when(menuFeedbackService.buildReportFeedback(eq(menuId), eq(from), eq(to)))
                .thenReturn(new AnalyticsDtos.ReportFeedback(
                        new AnalyticsDtos.MenuFeedbackSummary(
                                java.math.BigDecimal.valueOf(4.5),
                                2L,
                                List.of(),
                                List.of()
                        ),
                        new AnalyticsDtos.ProductFeedbackSummary(
                                java.math.BigDecimal.valueOf(3.0),
                                1L,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                ));

        AnalyticsDtos.MenuAnalyticsReportResponse report = service.getMenuReport(menuId, ownerId, from, to);

        assertThat(report.menuId()).isEqualTo(menuId);
        assertThat(report.kpis().sessions()).isEqualTo(4L);
        assertThat(report.kpis().menuOpens()).isEqualTo(5L);
        assertThat(report.kpis().productViews()).isEqualTo(8L);
        assertThat(report.kpis().avgProductsPerSession()).isEqualTo(2.0);
        assertThat(report.daily()).hasSize(2);
        assertThat(report.daily().getFirst().sessions()).isEqualTo(2L);
        assertThat(report.hourly()).hasSize(24);
        assertThat(report.topCategories().getFirst().name()).isEqualTo("Icecek");
        assertThat(report.feedback()).isNotNull();
        assertThat(report.feedback().menu().ratingCount()).isEqualTo(2L);
        assertThat(report.feedback().products().ratingCount()).isEqualTo(1L);
    }

    @Test
    void getMenuRevenueReport_whenConfirmedOrders_thenSplitsSpotlightHourlyAndUnsold() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(publicMenu(menuId, ownerId)));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId))
                .thenReturn(List.of(
                        catalogProduct(menuId, 1L, "Ayran", 0),
                        catalogProduct(menuId, 2L, "Izgara", 1),
                        catalogProduct(menuId, 3L, "Cay", 2),
                        catalogProduct(menuId, 4L, "Salata", 3)
                ));
        when(subCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(SubCategory.builder()
                        .id(1L)
                        .mainCategoryId(1L)
                        .slug("icecek")
                        .name("Icecek")
                        .sortOrder(1)
                        .build()));

        MenuOrder order = MenuOrder.builder()
                .id(20L)
                .menuId(menuId)
                .tableId(1L)
                .tableSessionId(UUID.randomUUID())
                .status(MenuOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("260.00"))
                .currency("TRY")
                .confirmedAt(LocalDateTime.of(2026, 8, 13, 14, 30))
                .build();
        order.addItem(line(1L, "Ayran", 10, "50.00"));
        order.addItem(line(2L, "Izgara", 2, "200.00"));
        order.addItem(line(3L, "Cay", 1, "10.00"));

        when(menuOrderRepository.findByMenuIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
                eq(menuId), eq(MenuOrderStatus.CONFIRMED), any(), any()
        )).thenReturn(List.of(order));

        AnalyticsDtos.MenuRevenueReportResponse report = service.getMenuRevenueReport(menuId, ownerId, day, day);

        assertThat(report.kpis().orderCount()).isEqualTo(1L);
        assertThat(report.kpis().itemCount()).isEqualTo(13L);
        assertThat(report.spotlight().byQuantity().name()).isEqualTo("Ayran");
        assertThat(report.spotlight().byRevenue().name()).isEqualTo("Izgara");
        assertThat(report.spotlight().leastSoldByQuantity().name()).isEqualTo("Cay");
        assertThat(report.unsold().count()).isEqualTo(1L);
        assertThat(report.unsold().products()).extracting(AnalyticsDtos.UnsoldProduct::name).containsExactly("Salata");
        assertThat(report.hourly()).hasSize(24);
        assertThat(report.hourly().get(14).orderCount()).isEqualTo(1L);
        assertThat(report.hourly().get(14).revenue()).isEqualByComparingTo("260.00");
    }

    @Test
    void getMenuWaiterPerformanceReport_whenConfirmedOrders_thenGroupsByWaiterAndUnassigned() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(publicMenu(menuId, ownerId)));

        MenuWaiter ali = MenuWaiter.builder()
                .id(101L)
                .ownerUserId(ownerId)
                .menuId(menuId)
                .username("ali")
                .passwordHash("hash")
                .displayName("Ali")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        MenuWaiter ayse = MenuWaiter.builder()
                .id(102L)
                .ownerUserId(ownerId)
                .menuId(menuId)
                .username("ayse")
                .passwordHash("hash")
                .displayName("Ayse")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(menuWaiterRepository.findByMenuIdOrderByDisplayNameAsc(menuId)).thenReturn(List.of(ali, ayse));

        MenuOrder aliOrder = confirmedOrder(menuId, 1L, new BigDecimal("150.00"), 101L);
        MenuOrder ayseOrder = confirmedOrder(menuId, 2L, new BigDecimal("90.00"), 102L);
        MenuOrder unassignedOrder = confirmedOrder(menuId, 3L, new BigDecimal("20.00"), null);

        when(menuOrderRepository.findByMenuIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
                eq(menuId), eq(MenuOrderStatus.CONFIRMED), any(), any()
        )).thenReturn(List.of(aliOrder, ayseOrder, unassignedOrder));

        AnalyticsDtos.MenuWaiterPerformanceReportResponse report =
                service.getMenuWaiterPerformanceReport(menuId, ownerId, day, day);

        assertThat(report.kpis().activeWaiterCount()).isEqualTo(2L);
        assertThat(report.kpis().assignedOrderCount()).isEqualTo(2L);
        assertThat(report.kpis().unassignedOrderCount()).isEqualTo(1L);
        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("260.00");
        assertThat(report.waiters()).extracting(AnalyticsDtos.WaiterPerformanceRow::displayName)
                .containsExactly("Ali", "Ayse", "Atanmamış");
        assertThat(report.waiters().getFirst().orderCount()).isEqualTo(1L);
        assertThat(report.waiters().getFirst().revenue()).isEqualByComparingTo("150.00");
        assertThat(report.waiters().get(2).waiterId()).isNull();
    }

    private MenuOrder confirmedOrder(Long menuId, Long id, BigDecimal total, Long waiterId) {
        return MenuOrder.builder()
                .id(id)
                .menuId(menuId)
                .tableId(1L)
                .tableSessionId(UUID.randomUUID())
                .status(MenuOrderStatus.CONFIRMED)
                .totalAmount(total)
                .currency("TRY")
                .waiterId(waiterId)
                .confirmedAt(LocalDateTime.of(2026, 8, 13, 14, 30))
                .build();
    }

    private Menu publicMenu(Long menuId, Long ownerId) {
        return Menu.builder()
                .menuId(menuId)
                .userId(ownerId)
                .qrId(1L)
                .themeId("soft")
                .businessName("Test")
                .active(true)
                .publicAccessEnabled(true)
                .build();
    }

    private MenuProduct cayProduct(Long menuId) {
        return MenuProduct.builder()
                .productId(11L)
                .menuId(menuId)
                .name("Cay")
                .subCategoryId(3L)
                .build();
    }

    private MenuProduct catalogProduct(Long menuId, Long productId, String name, int sortOrder) {
        return MenuProduct.builder()
                .productId(productId)
                .menuId(menuId)
                .name(name)
                .subCategoryId(1L)
                .sortOrder(sortOrder)
                .build();
    }

    private MenuOrderItem line(Long productId, String name, int quantity, String lineTotal) {
        return MenuOrderItem.builder()
                .productId(productId)
                .productName(name)
                .quantity(quantity)
                .unitPrice(new BigDecimal(lineTotal))
                .lineTotal(new BigDecimal(lineTotal))
                .build();
    }
}
