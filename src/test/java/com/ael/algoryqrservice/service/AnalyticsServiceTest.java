package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAnalyticsEvent;
import com.ael.algoryqrservice.model.MenuAnalyticsSession;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsConnectionRepository;
import com.ael.algoryqrservice.integration.ubereats.repository.UberEatsOrderRepository;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsEventRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductVisitRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.repository.MenuVisitRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
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
    private MenuSubCategoryRepository menuSubCategoryRepository;
    @Mock
    private MenuFeedbackService menuFeedbackService;
    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private MenuWaiterRepository menuWaiterRepository;
    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private MenuFixedExpenseService menuFixedExpenseService;
    @Mock
    private BranchService branchService;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private UberEatsConnectionRepository uberEatsConnectionRepository;
    @Mock
    private UberEatsOrderRepository uberEatsOrderRepository;

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
                menuSubCategoryRepository,
                menuFeedbackService,
                menuOrderRepository,
                menuWaiterRepository,
                billPaymentRepository,
                tableBillRepository,
                menuFixedExpenseService,
                branchService,
                branchRepository,
                uberEatsConnectionRepository,
                uberEatsOrderRepository
        );
    }

    @Test
    void recordEvents_whenValidBatch_thenUpsertsSessionAndSavesEvents() {
        UUID sessionId = UUID.randomUUID();
        Menu menu = publicMenu(5L, 9L);
        when(menuRepository.findById(5L)).thenReturn(Optional.of(menu));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(eventRepository.countBySessionIdAndMenuId(sessionId, 5L)).thenReturn(0L);
        when(menuSubCategoryRepository.findByIdAndDeletedFalse(3L))
                .thenReturn(Optional.of(MenuSubCategory.builder().id(3L).menuId(5L).menuCategoryId(1L).slug("taze").name("Icecek").sortOrder(1).build()));
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
        when(sessionRepository.countByMenuIdInAndPeriod(eq(List.of(menuId)), any(), any())).thenReturn(4L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(menuId)), eq(MenuAnalyticsEventType.MENU_OPEN), any(), any())).thenReturn(5L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(menuId)), eq(MenuAnalyticsEventType.PRODUCT_VIEW), any(), any())).thenReturn(8L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(menuId)), eq(MenuAnalyticsEventType.CATEGORY_VIEW), any(), any())).thenReturn(6L);
        when(eventRepository.avgProductsPerSessionByMenuIds(eq(List.of(menuId)), any(), any())).thenReturn(2.0);
        when(sessionRepository.countDailyByMenuIdInAndPeriod(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf(from), 2L}));
        when(eventRepository.countDailyOpenAndProductByMenuIdIn(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf(from), 3L, 4L}));
        when(eventRepository.countHourlyByMenuIdIn(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{12, 7L}));
        when(sessionRepository.countByDeviceTypeAndPeriodForMenuIds(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"MOBILE", 3L}, new Object[]{"DESKTOP", 1L}));
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(menuId)))
                .thenReturn(List.of(cayProduct(menuId)));
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any()))
                .thenReturn(List.of(MenuSubCategory.builder().id(3L).menuId(menuId).menuCategoryId(1L).slug("icecek").name("Icecek").sortOrder(1).build()));
        when(eventRepository.topProductsByMenuIds(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 8L}));
        when(eventRepository.topCategoriesByMenuIds(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 6L}));
        when(eventRepository.productViewsByCategoryForMenuIds(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, 11L, 8L}));
        when(sessionRepository.findRecentByMenuIdInAndPeriod(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.of());
        when(menuFeedbackService.buildReportFeedback(eq(List.of(menuId)), eq(from), eq(to)))
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
    void getMenuRevenueReport_whenBillPayments_thenSplitsSpotlightHourlyAndUnsold() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        Menu menu = publicMenu(menuId, ownerId);
        menu.setBranchId(2L);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(menuId)))
                .thenReturn(List.of(
                        catalogProduct(menuId, 1L, "Ayran", 0),
                        catalogProduct(menuId, 2L, "Izgara", 1),
                        catalogProduct(menuId, 3L, "Cay", 2),
                        catalogProduct(menuId, 4L, "Salata", 3)
                ));
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any()))
                .thenReturn(List.of(MenuSubCategory.builder()
                        .id(1L)
                        .menuId(menuId)
                        .menuCategoryId(1L)
                        .slug("icecek")
                        .name("Icecek")
                        .sortOrder(1)
                        .build()));
        when(menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(2L)).thenReturn(List.of());
        when(menuFixedExpenseService.totalDailyActiveAmount(List.of(menuId))).thenReturn(BigDecimal.ZERO);
        when(uberEatsConnectionRepository.findByUserId(ownerId)).thenReturn(Optional.empty());

        TableBill bill = TableBill.builder().id(20L).menuId(menuId).currency("TRY").build();
        TableBillItem ayran = TableBillItem.builder()
                .id(1L).productId(1L).productName("Ayran").quantity(10).paidQuantity(10).build();
        TableBillItem izgara = TableBillItem.builder()
                .id(2L).productId(2L).productName("Izgara").quantity(2).paidQuantity(2).build();
        TableBillItem cay = TableBillItem.builder()
                .id(3L).productId(3L).productName("Cay").quantity(1).paidQuantity(1).build();
        LocalDateTime paidAt = LocalDateTime.of(2026, 8, 13, 14, 30);

        when(billPaymentRepository.findByMenuIdInAndPaidAtBetween(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.of(
                        payment(bill, ayran, new BigDecimal("50.00"), 10, paidAt),
                        payment(bill, izgara, new BigDecimal("200.00"), 2, paidAt),
                        payment(bill, cay, new BigDecimal("10.00"), 1, paidAt)
                ));

        AnalyticsDtos.MenuRevenueReportResponse report = service.getMenuRevenueReport(menuId, ownerId, day, day);

        assertThat(report.kpis().orderCount()).isEqualTo(1L);
        assertThat(report.kpis().itemCount()).isEqualTo(13L);
        assertThat(report.spotlight().byQuantity().name()).isEqualTo("Ayran");
        assertThat(report.spotlight().byRevenue().name()).isEqualTo("Izgara");
        assertThat(report.spotlight().leastSoldByQuantity().name()).isEqualTo("Cay");
        assertThat(report.unsold().count()).isEqualTo(1L);
        assertThat(report.unsold().products()).extracting(AnalyticsDtos.UnsoldProduct::name).containsExactly("Salata");
        assertThat(report.hourly()).hasSize(24);
        assertThat(report.hourly().get(14).orderCount()).isEqualTo(3L);
        assertThat(report.hourly().get(14).revenue()).isEqualByComparingTo("260.00");
        assertThat(report.paymentBreakdown().grossRevenue()).isEqualByComparingTo("260.00");
        assertThat(report.paymentBreakdown().uberEatsRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void getMenuRevenueReport_whenUberEatsAcceptedAndPrepared_thenAddsToGrossAndExcludesRejected() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        Menu menu = publicMenu(menuId, ownerId);
        menu.setBranchId(2L);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(menuId)))
                .thenReturn(List.of());
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any())).thenReturn(List.of());
        when(menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(2L)).thenReturn(List.of());
        when(menuFixedExpenseService.totalDailyActiveAmount(List.of(menuId))).thenReturn(BigDecimal.ZERO);
        when(billPaymentRepository.findByMenuIdInAndPaidAtBetween(eq(List.of(menuId)), any(), any()))
                .thenReturn(List.of());

        UberEatsConnection connection = new UberEatsConnection();
        connection.setId(42L);
        connection.setUserId(ownerId);
        when(uberEatsConnectionRepository.findByUserId(ownerId)).thenReturn(Optional.of(connection));
        when(uberEatsOrderRepository.sumRevenueByConnectionAndStatuses(
                eq(42L), any(), any(), eq(List.of("accepted", "prepared"))
        )).thenReturn(new BigDecimal("175.50"));

        AnalyticsDtos.MenuRevenueReportResponse report = service.getMenuRevenueReport(menuId, ownerId, day, day);

        assertThat(report.paymentBreakdown().uberEatsRevenue()).isEqualByComparingTo("175.50");
        assertThat(report.paymentBreakdown().grossRevenue()).isEqualByComparingTo("175.50");
        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("175.50");
        verify(uberEatsOrderRepository).sumRevenueByConnectionAndStatuses(
                eq(42L), any(), any(), eq(List.of("accepted", "prepared"))
        );
    }

    private BillPayment payment(
            TableBill bill,
            TableBillItem item,
            BigDecimal amount,
            int quantityPaid,
            LocalDateTime paidAt
    ) {
        return BillPayment.builder()
                .bill(bill)
                .billItem(item)
                .paymentMethod(TableBillPaymentMethod.CASH)
                .amount(amount)
                .quantityPaid(quantityPaid)
                .tip(false)
                .paidAt(paidAt)
                .createdAt(paidAt)
                .build();
    }

    @Test
    void getMenuWaiterPerformanceReport_whenConfirmedOrders_thenGroupsByWaiterAndUnassigned() {
        Long menuId = 5L;
        Long ownerId = 9L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        Menu menu = publicMenu(menuId, ownerId);
        menu.setBranchId(2L);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));

        MenuWaiter ali = MenuWaiter.builder()
                .id(101L)
                .ownerUserId(ownerId)
                .branchId(2L)
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
                .branchId(2L)
                .username("ayse")
                .passwordHash("hash")
                .displayName("Ayse")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(2L)).thenReturn(List.of(ali, ayse));
        when(tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetween(
                eq(List.of(menuId)), eq(TableBillStatus.CLOSED), any(), any()
        )).thenReturn(List.of(
                TableBill.builder()
                        .id(10L)
                        .menuId(menuId)
                        .tableId(1L)
                        .status(TableBillStatus.CLOSED)
                        .closedByWaiterId(101L)
                        .closedAt(LocalDateTime.of(2026, 8, 13, 18, 0))
                        .build()
        ));

        MenuOrder aliOrder = confirmedOrder(menuId, 1L, new BigDecimal("150.00"), 101L, 2, new BigDecimal("15.00"));
        MenuOrder ayseOrder = confirmedOrder(menuId, 2L, new BigDecimal("90.00"), 102L, 1, new BigDecimal("9.00"));
        MenuOrder unassignedOrder = confirmedOrder(menuId, 3L, new BigDecimal("20.00"), null, 1, BigDecimal.ZERO);

        when(menuOrderRepository.findByMenuIdInAndStatusAndConfirmedAtBetweenOrderByConfirmedAtAsc(
                eq(List.of(menuId)), eq(MenuOrderStatus.CONFIRMED), any(), any()
        )).thenReturn(List.of(aliOrder, ayseOrder, unassignedOrder));

        AnalyticsDtos.MenuWaiterPerformanceReportResponse report =
                service.getMenuWaiterPerformanceReport(menuId, ownerId, day, day);

        assertThat(report.kpis().activeWaiterCount()).isEqualTo(2L);
        assertThat(report.kpis().assignedOrderCount()).isEqualTo(2L);
        assertThat(report.kpis().unassignedOrderCount()).isEqualTo(1L);
        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("260.00");
        assertThat(report.kpis().itemCount()).isEqualTo(4L);
        assertThat(report.kpis().totalCommission()).isEqualByComparingTo("24.00");
        assertThat(report.kpis().billsClosedCount()).isEqualTo(1L);
        assertThat(report.waiters()).extracting(AnalyticsDtos.WaiterPerformanceRow::displayName)
                .containsExactly("Ali", "Ayse", "Atanmamış");
        assertThat(report.waiters().getFirst().orderCount()).isEqualTo(1L);
        assertThat(report.waiters().getFirst().itemCount()).isEqualTo(2L);
        assertThat(report.waiters().getFirst().revenue()).isEqualByComparingTo("150.00");
        assertThat(report.waiters().getFirst().commissionAmount()).isEqualByComparingTo("15.00");
        assertThat(report.waiters().getFirst().billsClosedCount()).isEqualTo(1L);
        assertThat(report.waiters().get(2).waiterId()).isNull();
        assertThat(report.products()).isNotEmpty();
        assertThat(report.daily()).hasSize(1);
        assertThat(report.hourly()).hasSize(24);
    }

    @Test
    void getBranchReport_whenTwoMenus_thenAggregatesKpis() {
        Long ownerId = 9L;
        Long branchId = 2L;
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 1);
        Menu first = publicMenu(5L, ownerId);
        first.setBranchId(branchId);
        first.setBusinessName("Ogle");
        Menu second = publicMenu(6L, ownerId);
        second.setBranchId(branchId);
        second.setBusinessName("Aksam");
        when(branchService.requireOwnedForUser(branchId, ownerId))
                .thenReturn(Branch.builder().id(branchId).userId(ownerId).name("Kadikoy").build());
        when(menuRepository.findByBranchIdAndDeletedFalse(branchId)).thenReturn(List.of(first, second));
        when(sessionRepository.countByMenuIdInAndPeriod(eq(List.of(5L, 6L)), any(), any())).thenReturn(10L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(5L, 6L)), eq(MenuAnalyticsEventType.MENU_OPEN), any(), any())).thenReturn(12L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(5L, 6L)), eq(MenuAnalyticsEventType.PRODUCT_VIEW), any(), any())).thenReturn(20L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(
                eq(List.of(5L, 6L)), eq(MenuAnalyticsEventType.CATEGORY_VIEW), any(), any())).thenReturn(8L);
        when(eventRepository.avgProductsPerSessionByMenuIds(eq(List.of(5L, 6L)), any(), any())).thenReturn(1.5);
        when(sessionRepository.countDailyByMenuIdInAndPeriod(eq(List.of(5L, 6L)), any(), any()))
                .thenReturn(List.of());
        when(eventRepository.countDailyOpenAndProductByMenuIdIn(eq(List.of(5L, 6L)), any(), any()))
                .thenReturn(List.of());
        when(eventRepository.countHourlyByMenuIdIn(eq(List.of(5L, 6L)), any(), any()))
                .thenReturn(List.of());
        when(sessionRepository.countByDeviceTypeAndPeriodForMenuIds(eq(List.of(5L, 6L)), any(), any()))
                .thenReturn(List.of());
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(5L, 6L)))
                .thenReturn(List.of());
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any())).thenReturn(List.of());
        when(eventRepository.topProductsByMenuIds(eq(List.of(5L, 6L)), any(), any())).thenReturn(List.of());
        when(eventRepository.topCategoriesByMenuIds(eq(List.of(5L, 6L)), any(), any())).thenReturn(List.of());
        when(eventRepository.productViewsByCategoryForMenuIds(eq(List.of(5L, 6L)), any(), any())).thenReturn(List.of());
        when(sessionRepository.findRecentByMenuIdInAndPeriod(eq(List.of(5L, 6L)), any(), any())).thenReturn(List.of());
        when(menuFeedbackService.buildReportFeedback(eq(List.of(5L, 6L)), eq(from), eq(to)))
                .thenReturn(new AnalyticsDtos.ReportFeedback(
                        new AnalyticsDtos.MenuFeedbackSummary(BigDecimal.ZERO, 0L, List.of(), List.of()),
                        new AnalyticsDtos.ProductFeedbackSummary(
                                BigDecimal.ZERO, 0L, List.of(), List.of(), List.of(), List.of())
                ));

        AnalyticsDtos.MenuAnalyticsReportResponse report =
                service.getBranchReport(branchId, null, ownerId, from, to);

        assertThat(report.menuId()).isNull();
        assertThat(report.branchId()).isEqualTo(branchId);
        assertThat(report.branchName()).isEqualTo("Kadikoy");
        assertThat(report.kpis().sessions()).isEqualTo(10L);
        assertThat(report.kpis().menuOpens()).isEqualTo(12L);
        assertThat(report.kpis().productViews()).isEqualTo(20L);
    }

    @Test
    void getBranchReport_whenMenuFilter_thenUsesSingleMenu() {
        Long ownerId = 9L;
        Long branchId = 2L;
        LocalDate day = LocalDate.of(2026, 7, 1);
        Menu first = publicMenu(5L, ownerId);
        first.setBranchId(branchId);
        first.setBusinessName("Ogle");
        Menu second = publicMenu(6L, ownerId);
        second.setBranchId(branchId);
        when(branchService.requireOwnedForUser(branchId, ownerId))
                .thenReturn(Branch.builder().id(branchId).userId(ownerId).name("Kadikoy").build());
        when(menuRepository.findByBranchIdAndDeletedFalse(branchId)).thenReturn(List.of(first, second));
        when(sessionRepository.countByMenuIdInAndPeriod(eq(List.of(5L)), any(), any())).thenReturn(3L);
        when(eventRepository.countByMenuIdInAndEventTypeAndOccurredAtBetween(any(), any(), any(), any())).thenReturn(0L);
        when(eventRepository.avgProductsPerSessionByMenuIds(eq(List.of(5L)), any(), any())).thenReturn(0d);
        when(sessionRepository.countDailyByMenuIdInAndPeriod(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(eventRepository.countDailyOpenAndProductByMenuIdIn(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(eventRepository.countHourlyByMenuIdIn(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(sessionRepository.countByDeviceTypeAndPeriodForMenuIds(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(5L)))
                .thenReturn(List.of());
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any())).thenReturn(List.of());
        when(eventRepository.topProductsByMenuIds(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(eventRepository.topCategoriesByMenuIds(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(eventRepository.productViewsByCategoryForMenuIds(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(sessionRepository.findRecentByMenuIdInAndPeriod(eq(List.of(5L)), any(), any())).thenReturn(List.of());
        when(menuFeedbackService.buildReportFeedback(eq(List.of(5L)), eq(day), eq(day)))
                .thenReturn(new AnalyticsDtos.ReportFeedback(
                        new AnalyticsDtos.MenuFeedbackSummary(BigDecimal.ZERO, 0L, List.of(), List.of()),
                        new AnalyticsDtos.ProductFeedbackSummary(
                                BigDecimal.ZERO, 0L, List.of(), List.of(), List.of(), List.of())
                ));

        AnalyticsDtos.MenuAnalyticsReportResponse report =
                service.getBranchReport(branchId, 5L, ownerId, day, day);

        assertThat(report.menuId()).isEqualTo(5L);
        assertThat(report.menuName()).isEqualTo("Ogle");
        assertThat(report.branchId()).isEqualTo(branchId);
        assertThat(report.kpis().sessions()).isEqualTo(3L);
    }

    @Test
    void getBranchReport_whenMenuNotInBranch_thenNotFound() {
        Long ownerId = 9L;
        Long branchId = 2L;
        when(branchService.requireOwnedForUser(branchId, ownerId))
                .thenReturn(Branch.builder().id(branchId).userId(ownerId).name("Kadikoy").build());
        when(menuRepository.findByBranchIdAndDeletedFalse(branchId)).thenReturn(List.of(publicMenu(5L, ownerId)));

        assertThatThrownBy(() -> service.getBranchReport(
                branchId, 99L, ownerId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)
        )).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void getBranchRevenueReport_whenTwoMenus_thenSumsPayments() {
        Long ownerId = 9L;
        Long branchId = 2L;
        LocalDate day = LocalDate.of(2026, 8, 13);
        Menu first = publicMenu(5L, ownerId);
        first.setBranchId(branchId);
        Menu second = publicMenu(6L, ownerId);
        second.setBranchId(branchId);
        when(branchService.requireOwnedForUser(branchId, ownerId))
                .thenReturn(Branch.builder().id(branchId).userId(ownerId).name("Kadikoy").build());
        when(menuRepository.findByBranchIdAndDeletedFalse(branchId)).thenReturn(List.of(first, second));
        when(menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(List.of(5L, 6L)))
                .thenReturn(List.of());
        when(menuSubCategoryRepository.findByIdInAndDeletedFalse(any())).thenReturn(List.of());
        when(menuWaiterRepository.findByBranchIdOrderByDisplayNameAsc(any())).thenReturn(List.of());
        when(menuFixedExpenseService.totalDailyActiveAmount(List.of(5L, 6L))).thenReturn(BigDecimal.ZERO);
        when(uberEatsConnectionRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
        TableBill bill = TableBill.builder().id(20L).menuId(5L).currency("TRY").build();
        TableBillItem item = TableBillItem.builder()
                .id(1L).productId(1L).productName("Ayran").quantity(1).paidQuantity(1).build();
        when(billPaymentRepository.findByMenuIdInAndPaidAtBetween(eq(List.of(5L, 6L)), any(), any()))
                .thenReturn(List.of(payment(bill, item, new BigDecimal("40.00"), 1, LocalDateTime.of(2026, 8, 13, 12, 0))));

        AnalyticsDtos.MenuRevenueReportResponse report =
                service.getBranchRevenueReport(branchId, null, ownerId, day, day);

        assertThat(report.branchId()).isEqualTo(branchId);
        assertThat(report.menuId()).isNull();
        assertThat(report.kpis().totalRevenue()).isEqualByComparingTo("40.00");
    }

    private MenuOrder confirmedOrder(
            Long menuId,
            Long id,
            BigDecimal total,
            Long waiterId,
            int itemQuantity,
            BigDecimal commission
    ) {
        MenuOrderItem item = MenuOrderItem.builder()
                .productId(11L)
                .productName("Cay")
                .quantity(itemQuantity)
                .lineTotal(total)
                .build();
        return MenuOrder.builder()
                .id(id)
                .menuId(menuId)
                .tableId(1L)
                .tableSessionId(UUID.randomUUID())
                .status(MenuOrderStatus.CONFIRMED)
                .totalAmount(total)
                .currency("TRY")
                .waiterId(waiterId)
                .commissionAmount(commission)
                .confirmedAt(LocalDateTime.of(2026, 8, 13, 14, 30))
                .items(new java.util.ArrayList<>(List.of(item)))
                .build();
    }

    private MenuOrder confirmedOrder(Long menuId, Long id, BigDecimal total, Long waiterId) {
        return confirmedOrder(menuId, id, total, waiterId, 1, BigDecimal.ZERO);
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
