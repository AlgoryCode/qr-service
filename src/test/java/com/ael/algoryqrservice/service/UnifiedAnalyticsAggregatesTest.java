package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.repository.BillAdjustmentRepository;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.MenuAnalyticsSessionRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.WorkShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedAnalyticsAggregatesTest {

    @Mock
    private MenuOrderRepository menuOrderRepository;
    @Mock
    private TableBillRepository tableBillRepository;
    @Mock
    private TableBillItemRepository tableBillItemRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private MenuWaiterRepository menuWaiterRepository;
    @Mock
    private MenuAnalyticsSessionRepository sessionRepository;
    @Mock
    private BillAdjustmentRepository billAdjustmentRepository;
    @Mock
    private WorkShiftRepository workShiftRepository;

    private UnifiedAnalyticsAggregates aggregates;

    @BeforeEach
    void setUp() {
        aggregates = new UnifiedAnalyticsAggregates(
                menuOrderRepository,
                tableBillRepository,
                tableBillItemRepository,
                restaurantTableRepository,
                billPaymentRepository,
                menuWaiterRepository,
                sessionRepository,
                billAdjustmentRepository,
                workShiftRepository
        );
    }

    @Test
    void buildCoPurchase_whenSharedBills_thenCompanionShareComputed() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        when(tableBillItemRepository.findProductRowsForClosedBills(
                any(), eq(TableBillStatus.CLOSED), any(), any()))
                .thenReturn(List.of(
                        new Object[]{1L, 10L, "Adana"},
                        new Object[]{1L, 20L, "Ayran"},
                        new Object[]{2L, 10L, "Adana"},
                        new Object[]{2L, 20L, "Ayran"},
                        new Object[]{2L, 30L, "Salata"},
                        new Object[]{3L, 10L, "Adana"},
                        new Object[]{3L, 20L, "Ayran"}
                ));

        List<AnalyticsDtos.ProductCoPurchase> result =
                aggregates.buildCoPurchase(List.of(5L), day, day);

        AnalyticsDtos.ProductCoPurchase adana = result.stream()
                .filter(p -> p.productId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(adana.billCount()).isEqualTo(3L);
        assertThat(adana.companions()).isNotEmpty();
        assertThat(adana.companions().getFirst().productId()).isEqualTo(20L);
        assertThat(adana.companions().getFirst().sharePercent()).isEqualTo(100d);
    }

    @Test
    void buildOrders_whenConfirmedAndCancelled_thenCancelRateAndAov() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        LocalDateTime noon = day.atTime(12, 0);
        MenuOrder confirmed = MenuOrder.builder()
                .id(1L)
                .menuId(5L)
                .tableId(7L)
                .tableSessionId(UUID.randomUUID())
                .status(MenuOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("100.00"))
                .confirmedAt(noon)
                .createdAt(noon)
                .build();
        confirmed.addItem(MenuOrderItem.builder()
                .productId(1L)
                .productName("Adana")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2)
                .lineTotal(new BigDecimal("200.00"))
                .build());
        MenuOrder cancelled = MenuOrder.builder()
                .id(2L)
                .menuId(5L)
                .tableId(7L)
                .tableSessionId(UUID.randomUUID())
                .status(MenuOrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("50.00"))
                .rejectedAt(noon.plusHours(1))
                .createdAt(noon.plusHours(1))
                .build();

        when(menuOrderRepository.findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(confirmed, cancelled));
        when(menuOrderRepository.countByStatusForMenuIds(any(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{MenuOrderStatus.CONFIRMED, 1L},
                        new Object[]{MenuOrderStatus.CANCELLED, 1L}
                ));

        AnalyticsDtos.OrdersAnalytics orders = aggregates.buildOrders(List.of(5L), day, day);

        assertThat(orders.completedOrders()).isEqualTo(1L);
        assertThat(orders.cancelledOrders()).isEqualTo(1L);
        assertThat(orders.cancelRate()).isEqualTo(50d);
        assertThat(orders.avgItemsPerOrder()).isEqualTo(2d);
        assertThat(orders.aov()).isEqualByComparingTo("100.00");
    }

    @Test
    void buildTables_whenClosedBill_thenRevenueAndDwell() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        RestaurantTable table = RestaurantTable.builder()
                .id(7L)
                .menuId(5L)
                .name("Bahce 1")
                .publicToken("tok")
                .active(true)
                .build();
        TableBill bill = TableBill.builder()
                .id(100L)
                .menuId(5L)
                .tableId(7L)
                .status(TableBillStatus.CLOSED)
                .totalAmount(new BigDecimal("180.00"))
                .openedAt(day.atTime(18, 0))
                .closedAt(day.atTime(19, 0))
                .build();
        when(restaurantTableRepository.findByMenuIdInOrderByTableNumberAscNameAsc(any()))
                .thenReturn(List.of(table));
        when(tableBillRepository.findByMenuIdInAndStatusAndClosedAtBetweenOrderByClosedAtAsc(
                any(), eq(TableBillStatus.CLOSED), any(), any()))
                .thenReturn(List.of(bill));
        when(menuOrderRepository.findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsDtos.TablesAnalytics tables = aggregates.buildTables(List.of(5L), day, day);

        assertThat(tables.perTable()).hasSize(1);
        assertThat(tables.perTable().getFirst().revenue()).isEqualByComparingTo("180.00");
        assertThat(tables.avgDwellMinutes()).isEqualTo(60d);
    }
}
